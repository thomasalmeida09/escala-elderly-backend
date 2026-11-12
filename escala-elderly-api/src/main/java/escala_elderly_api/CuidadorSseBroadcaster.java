package escala_elderly_api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hub concorrente de transmissao em tempo real via Server-Sent Events (W3C SSE / SseEmitter).
 *
 * <p>Garante linearizabilidade entre o snapshot inicial na conexao e os broadcasts subsequentes,
 * isolamento estrito de privacidade LGPD por assinante (?privacy=true vs. ?privacy=false),
 * pre-serializacao O(1) via Jackson 3 sem double-quoting, heartbeats periodicos anti-timeout de
 * proxy e teto deterministico de conexoes simultaneas com eviccao FIFO.
 */
@Component
public class CuidadorSseBroadcaster implements DisposableBean {

    public static final String EVENT_SCHEDULE_UPDATED = "SCHEDULE_UPDATED";
    public static final String COMMENT_HEARTBEAT = "heartbeat";
    public static final MediaType UTF8_JSON_STRING_MEDIA_TYPE =
            new MediaType("text", "plain", StandardCharsets.UTF_8);

    public static final long DEFAULT_TIMEOUT_MS = 1_800_000L;
    public static final int DEFAULT_MAX_SUBSCRIBERS = 256;
    public static final long DEFAULT_RETRY_MS = 3_000L;
    public static final long DEFAULT_HEARTBEAT_MS = 25_000L;

    /** Par imutavel e indivisivel (versao monotonica >= 1, lista imutavel de cuidadores). */
    public record SnapshotEscala(long versao, List<Cuidador> cuidadores) {
        public SnapshotEscala {
            if (versao < 1L) {
                throw new IllegalArgumentException(
                        "A versao do snapshot da escala deve ser maior ou igual a 1.");
            }
            Objects.requireNonNull(
                    cuidadores, "A lista de cuidadores do snapshot nao pode ser nula.");
            cuidadores = List.copyOf(cuidadores);
        }
    }

    private static final class SseSubscription {
        final UUID id;
        final SseEmitter emitter;
        final boolean modoPrivacidade;
        final Object sendMonitor = new Object();
        long ultimoSeqEnviado = 0L;
        volatile boolean encerrada = false;

        SseSubscription(UUID id, SseEmitter emitter, boolean modoPrivacidade) {
            this.id = id;
            this.emitter = emitter;
            this.modoPrivacidade = modoPrivacidade;
        }
    }

    private final JsonMapper jsonMapper;
    private final long timeoutMs;
    private final int maxSubscribers;
    private final long retryMs;
    private final Executor broadcastExecutor;
    private final ExecutorService ownedBroadcastExecutor;
    private final ScheduledExecutorService heartbeatScheduler;
    private final LongFunction<SseEmitter> emitterFactory;
    private final UnaryOperator<List<Cuidador>> lgpdProjector;

    private final Object stateAndRegistrationLock = new Object();
    private final ConcurrentHashMap<UUID, SseSubscription> assinantes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<UUID> ordemChegada = new ConcurrentLinkedDeque<>();
    private final AtomicInteger assinantesPrivacidadeAtivos = new AtomicInteger(0);
    private final AtomicBoolean drenagemAgendada = new AtomicBoolean(false);
    private final AtomicBoolean encerrado = new AtomicBoolean(false);
    private final AtomicLong falhasTransporteDescartadas = new AtomicLong(0L);

    private volatile SnapshotEscala ultimoEstadoConhecido;
    private volatile long ultimaVersaoDrenada = 0L;

    @Autowired
    public CuidadorSseBroadcaster(
            JsonMapper jsonMapper,
            @Value("${escala.sse.timeout-ms:1800000}") long timeoutMs,
            @Value("${escala.sse.max-subscribers:256}") int maxSubscribers,
            @Value("${escala.sse.retry-ms:3000}") long retryMs,
            @Value("${escala.sse.heartbeat-ms:25000}") long heartbeatMs) {
        this(
                jsonMapper,
                timeoutMs,
                maxSubscribers,
                retryMs,
                heartbeatMs,
                criarExecutorBroadcastDaemon(),
                true,
                SseEmitter::new,
                LgpdPrivacyMasker::projetarListaPublica);
    }

    CuidadorSseBroadcaster(
            JsonMapper jsonMapper,
            long timeoutMs,
            int maxSubscribers,
            long retryMs,
            long heartbeatMs,
            Executor broadcastExecutor,
            LongFunction<SseEmitter> emitterFactory,
            UnaryOperator<List<Cuidador>> lgpdProjector) {
        this(
                jsonMapper,
                timeoutMs,
                maxSubscribers,
                retryMs,
                heartbeatMs,
                broadcastExecutor,
                false,
                emitterFactory,
                lgpdProjector);
    }

    private CuidadorSseBroadcaster(
            JsonMapper jsonMapper,
            long timeoutMs,
            int maxSubscribers,
            long retryMs,
            long heartbeatMs,
            Executor broadcastExecutor,
            boolean ownsBroadcastExecutor,
            LongFunction<SseEmitter> emitterFactory,
            UnaryOperator<List<Cuidador>> lgpdProjector) {
        Objects.requireNonNull(jsonMapper, "JsonMapper nao pode ser nulo.");
        Objects.requireNonNull(broadcastExecutor, "broadcastExecutor nao pode ser nulo.");
        Objects.requireNonNull(emitterFactory, "emitterFactory nao pode ser nulo.");
        Objects.requireNonNull(lgpdProjector, "lgpdProjector nao pode ser nulo.");
        if (maxSubscribers < 1) {
            throw new IllegalArgumentException(
                    "escala.sse.max-subscribers deve ser maior ou igual a 1.");
        }
        if (timeoutMs < 0L) {
            throw new IllegalArgumentException(
                    "escala.sse.timeout-ms deve ser maior ou igual a 0.");
        }
        if (retryMs < 0L) {
            throw new IllegalArgumentException("escala.sse.retry-ms deve ser maior ou igual a 0.");
        }

        this.jsonMapper = jsonMapper;
        this.timeoutMs = timeoutMs;
        this.maxSubscribers = maxSubscribers;
        this.retryMs = retryMs;
        this.broadcastExecutor = broadcastExecutor;
        this.ownedBroadcastExecutor =
                ownsBroadcastExecutor ? (ExecutorService) broadcastExecutor : null;
        this.emitterFactory = emitterFactory;
        this.lgpdProjector = lgpdProjector;

        if (heartbeatMs > 0L) {
            ScheduledExecutorService scheduler =
                    Executors.newSingleThreadScheduledExecutor(
                            runnable -> {
                                Thread thread = new Thread(runnable, "escala-sse-heartbeat");
                                thread.setDaemon(true);
                                return thread;
                            });
            scheduler.scheduleWithFixedDelay(
                    this::enviarHeartbeatPeriodico,
                    heartbeatMs,
                    heartbeatMs,
                    TimeUnit.MILLISECONDS);
            this.heartbeatScheduler = scheduler;
        } else {
            this.heartbeatScheduler = null;
        }
    }

    /**
     * Instancia deterministica leve para testes unitarios sem container Spring (zero threads em
     * background, executor sincrono Runnable::run e heartbeat desativado).
     */
    public static CuidadorSseBroadcaster padraoParaTestes() {
        return new CuidadorSseBroadcaster(
                JsonMapper.builder().build(),
                DEFAULT_TIMEOUT_MS,
                DEFAULT_MAX_SUBSCRIBERS,
                DEFAULT_RETRY_MS,
                0L,
                Runnable::run,
                SseEmitter::new,
                LgpdPrivacyMasker::projetarListaPublica);
    }

    private static ExecutorService criarExecutorBroadcastDaemon() {
        return Executors.newSingleThreadExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "escala-sse-broadcaster");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * Registra um novo assinante SSE aplicando eviccao FIFO se atingir maxSubscribers e envia
     * imediatamente o snapshot mais recente sem risco de atualizacao perdida ou inversao de ordem.
     */
    public SseEmitter registrarAssinante(SnapshotEscala snapshotServico, boolean modoPrivacidade) {
        Objects.requireNonNull(snapshotServico, "Snapshot inicial do stream nao pode ser nulo.");
        SseEmitter emitter = emitterFactory.apply(timeoutMs);
        if (encerrado.get()) {
            encerrarEmitterComSeguranca(emitter);
            return emitter;
        }

        UUID id = UUID.randomUUID();
        SseSubscription nova = new SseSubscription(id, emitter, modoPrivacidade);

        emitter.onCompletion(() -> removerAssinante(id));
        emitter.onTimeout(() -> removerEEncerrarAssinante(nova));
        emitter.onError(ex -> removerEEncerrarAssinante(nova));

        List<SseSubscription> evictadas = new ArrayList<>();
        boolean canceladoPorShutdown = false;
        boolean deveDrenarExistentes = false;
        synchronized (stateAndRegistrationLock) {
            if (encerrado.get()) {
                canceladoPorShutdown = true;
            } else {
                if (ultimoEstadoConhecido == null
                        || snapshotServico.versao() > ultimoEstadoConhecido.versao()) {
                    ultimoEstadoConhecido = snapshotServico;
                }
                while (assinantes.size() >= maxSubscribers) {
                    UUID idMaisAntigo = ordemChegada.pollFirst();
                    if (idMaisAntigo == null) {
                        break;
                    }
                    SseSubscription antiga = assinantes.remove(idMaisAntigo);
                    if (antiga != null) {
                        antiga.encerrada = true;
                        if (antiga.modoPrivacidade) {
                            assinantesPrivacidadeAtivos.decrementAndGet();
                        }
                        evictadas.add(antiga);
                    }
                }
                boolean haviaAssinantesAtivos = !assinantes.isEmpty();
                assinantes.put(id, nova);
                ordemChegada.addLast(id);
                if (modoPrivacidade) {
                    assinantesPrivacidadeAtivos.incrementAndGet();
                }
                if (haviaAssinantesAtivos && ultimoEstadoConhecido.versao() > ultimaVersaoDrenada) {
                    deveDrenarExistentes = true;
                } else if (!haviaAssinantesAtivos) {
                    ultimaVersaoDrenada =
                            Math.max(ultimaVersaoDrenada, ultimoEstadoConhecido.versao());
                }
            }
        }

        if (canceladoPorShutdown) {
            encerrarEmitterComSeguranca(emitter);
            return emitter;
        }

        for (SseSubscription antiga : evictadas) {
            encerrarEmitterComSeguranca(antiga.emitter);
        }

        boolean falhouEnvioInicial = false;
        synchronized (nova.sendMonitor) {
            if (!nova.encerrada) {
                SnapshotEscala estadoMaisRecente = ultimoEstadoConhecido;
                if (estadoMaisRecente != null
                        && nova.ultimoSeqEnviado < estadoMaisRecente.versao()) {
                    try {
                        List<Cuidador> listaAlvo =
                                modoPrivacidade
                                        ? lgpdProjector.apply(estadoMaisRecente.cuidadores())
                                        : estadoMaisRecente.cuidadores();
                        String payloadJson = jsonMapper.writeValueAsString(listaAlvo);
                        enviarEventoScheduleUpdated(
                                nova.emitter, estadoMaisRecente.versao(), payloadJson);
                        nova.ultimoSeqEnviado = estadoMaisRecente.versao();
                    } catch (IOException | RuntimeException ex) {
                        nova.encerrada = true;
                        falhouEnvioInicial = true;
                        falhasTransporteDescartadas.incrementAndGet();
                    }
                }
            }
        }

        if (falhouEnvioInicial) {
            removerEEncerrarAssinante(nova);
        }
        if (deveDrenarExistentes) {
            agendarDrenagem();
        }

        return emitter;
    }

    /**
     * Publica assincronamente (com coalescencia latest-state) uma nova versao da escala para todos
     * os clientes SSE ativos, descartando versoes atrasadas fora de ordem.
     */
    public void publicarAtualizacao(SnapshotEscala novoSnapshot) {
        Objects.requireNonNull(novoSnapshot, "O snapshot para broadcast SSE nao pode ser nulo.");
        if (encerrado.get()) {
            return;
        }

        boolean pendenteParaDrenar;
        synchronized (stateAndRegistrationLock) {
            if (ultimoEstadoConhecido == null
                    || novoSnapshot.versao() > ultimoEstadoConhecido.versao()) {
                ultimoEstadoConhecido = novoSnapshot;
            }
            pendenteParaDrenar =
                    ultimoEstadoConhecido != null
                            && novoSnapshot.versao() >= ultimoEstadoConhecido.versao()
                            && ultimoEstadoConhecido.versao() > ultimaVersaoDrenada;
        }

        if (!pendenteParaDrenar || assinantes.isEmpty()) {
            return;
        }
        agendarDrenagem();
    }

    private void agendarDrenagem() {
        if (!drenagemAgendada.compareAndSet(false, true)) {
            return;
        }
        try {
            broadcastExecutor.execute(this::executarLoopDrenagem);
        } catch (RejectedExecutionException ex) {
            drenagemAgendada.set(false);
            falhasTransporteDescartadas.incrementAndGet();
        }
    }

    private void executarLoopDrenagem() {
        while (!encerrado.get()) {
            SnapshotEscala alvo = ultimoEstadoConhecido;
            if (alvo != null && alvo.versao() > ultimaVersaoDrenada) {
                transmitirSnapshotParaAssinantes(alvo);
                ultimaVersaoDrenada = Math.max(ultimaVersaoDrenada, alvo.versao());
            }
            drenagemAgendada.set(false);
            SnapshotEscala verificacaoPos = ultimoEstadoConhecido;
            if (encerrado.get()
                    || verificacaoPos == null
                    || verificacaoPos.versao() <= ultimaVersaoDrenada
                    || assinantes.isEmpty()) {
                return;
            }
            if (!drenagemAgendada.compareAndSet(false, true)) {
                return;
            }
        }
    }

    private void transmitirSnapshotParaAssinantes(SnapshotEscala snapshot) {
        Collection<SseSubscription> atuais = assinantes.values();
        if (atuais.isEmpty()) {
            return;
        }

        String jsonCanonico = null;
        String jsonPrivado = null;

        for (SseSubscription sub : atuais) {
            if (sub.encerrada) {
                continue;
            }

            boolean falhouCliente = false;
            synchronized (sub.sendMonitor) {
                if (!sub.encerrada && snapshot.versao() > sub.ultimoSeqEnviado) {
                    try {
                        String payload;
                        if (sub.modoPrivacidade) {
                            if (jsonPrivado == null) {
                                List<Cuidador> projetada =
                                        lgpdProjector.apply(snapshot.cuidadores());
                                jsonPrivado = jsonMapper.writeValueAsString(projetada);
                            }
                            payload = jsonPrivado;
                        } else {
                            if (jsonCanonico == null) {
                                jsonCanonico = jsonMapper.writeValueAsString(snapshot.cuidadores());
                            }
                            payload = jsonCanonico;
                        }
                        enviarEventoScheduleUpdated(sub.emitter, snapshot.versao(), payload);
                        sub.ultimoSeqEnviado = snapshot.versao();
                    } catch (IOException | RuntimeException ex) {
                        sub.encerrada = true;
                        falhouCliente = true;
                        falhasTransporteDescartadas.incrementAndGet();
                    }
                }
            }

            if (falhouCliente) {
                removerEEncerrarAssinante(sub);
            }
        }
    }

    private void enviarEventoScheduleUpdated(SseEmitter emitter, long versao, String payloadJson)
            throws IOException {
        emitter.send(
                SseEmitter.event()
                        .id(String.valueOf(versao))
                        .name(EVENT_SCHEDULE_UPDATED)
                        .reconnectTime(retryMs)
                        .data(payloadJson, UTF8_JSON_STRING_MEDIA_TYPE));
    }

    /** Envia comentario ':heartbeat' mantendo proxies vivos e expurgando sockets quebrados. */
    void enviarHeartbeatPeriodico() {
        if (encerrado.get() || assinantes.isEmpty()) {
            return;
        }

        for (SseSubscription sub : assinantes.values()) {
            if (sub.encerrada) {
                continue;
            }

            boolean falhouHeartbeat = false;
            synchronized (sub.sendMonitor) {
                if (!sub.encerrada) {
                    try {
                        sub.emitter.send(SseEmitter.event().comment(COMMENT_HEARTBEAT));
                    } catch (IOException | RuntimeException ex) {
                        sub.encerrada = true;
                        falhouHeartbeat = true;
                        falhasTransporteDescartadas.incrementAndGet();
                    }
                }
            }

            if (falhouHeartbeat) {
                removerEEncerrarAssinante(sub);
            }
        }
    }

    private void removerEEncerrarAssinante(SseSubscription sub) {
        sub.encerrada = true;
        removerAssinante(sub.id);
        encerrarEmitterComSeguranca(sub.emitter);
    }

    void removerAssinante(UUID id) {
        synchronized (stateAndRegistrationLock) {
            SseSubscription removida = assinantes.remove(id);
            if (removida != null) {
                removida.encerrada = true;
                ordemChegada.remove(id);
                if (removida.modoPrivacidade) {
                    assinantesPrivacidadeAtivos.decrementAndGet();
                }
            }
        }
    }

    private void encerrarEmitterComSeguranca(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ex) {
            // Contabiliza tentativas de complete() em sockets ja abortados pelo cliente (zero catch
            // vazio).
            falhasTransporteDescartadas.incrementAndGet();
        }
    }

    public int contarAssinantesAtivos() {
        return assinantes.size();
    }

    int contarOrdemChegadaRegistrada() {
        return ordemChegada.size();
    }

    int contarAssinantesPrivacidadeAtivos() {
        return assinantesPrivacidadeAtivos.get();
    }

    long obterFalhasTransporteDescartadas() {
        return falhasTransporteDescartadas.get();
    }

    boolean estaEncerrado() {
        return encerrado.get();
    }

    @Override
    public void destroy() {
        if (!encerrado.compareAndSet(false, true)) {
            return;
        }
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
        }
        if (ownedBroadcastExecutor != null) {
            ownedBroadcastExecutor.shutdownNow();
        }

        List<SseSubscription> restantes;
        synchronized (stateAndRegistrationLock) {
            restantes = new ArrayList<>(assinantes.values());
            for (SseSubscription sub : restantes) {
                sub.encerrada = true;
            }
            assinantes.clear();
            ordemChegada.clear();
            assinantesPrivacidadeAtivos.set(0);
        }

        for (SseSubscription sub : restantes) {
            encerrarEmitterComSeguranca(sub.emitter);
        }
    }
}
