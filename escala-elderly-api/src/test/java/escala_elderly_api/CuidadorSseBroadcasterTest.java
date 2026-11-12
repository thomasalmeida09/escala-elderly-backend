package escala_elderly_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class CuidadorSseBroadcasterTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /**
     * SseEmitter instrumentado para testes unitarios deterministicos em 0ms sem container Servlet:
     * captura os frames W3C SSE brutos enviados em send() e expoe gatilhos para onCompletion,
     * onTimeout, onError e IOException forcada.
     */
    private static final class TestSseEmitter extends SseEmitter {
        private final List<String> rawFrames = new CopyOnWriteArrayList<>();
        private final List<MediaType> dataMediaTypes = new CopyOnWriteArrayList<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final AtomicReference<IOException> nextSendFailure = new AtomicReference<>(null);
        private Runnable completionCallback;
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private Runnable beforeSendHook;

        TestSseEmitter(Long timeout) {
            super(timeout);
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            super.onCompletion(callback);
            this.completionCallback = callback;
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            super.onTimeout(callback);
            this.timeoutCallback = callback;
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            super.onError(callback);
            this.errorCallback = callback;
        }

        @Override
        public synchronized void send(SseEmitter.SseEventBuilder builder) throws IOException {
            send(builder.build());
        }

        @Override
        public synchronized void send(Set<ResponseBodyEmitter.DataWithMediaType> items)
                throws IOException {
            Runnable hook = this.beforeSendHook;
            if (hook != null) {
                this.beforeSendHook = null;
                hook.run();
            }
            IOException forced = nextSendFailure.getAndSet(null);
            if (forced != null) {
                throw forced;
            }
            if (completed.get()) {
                throw new IllegalStateException("Emitter ja encerrado.");
            }
            StringBuilder frame = new StringBuilder();
            for (ResponseBodyEmitter.DataWithMediaType item : items) {
                frame.append(item.getData());
                if (item.getMediaType() != null) {
                    dataMediaTypes.add(item.getMediaType());
                }
            }
            rawFrames.add(frame.toString());
        }

        @Override
        public synchronized void complete() {
            if (completed.compareAndSet(false, true) && completionCallback != null) {
                completionCallback.run();
            }
        }

        void failNextSend(String message) {
            nextSendFailure.set(new IOException(message));
        }

        void setBeforeSendHook(Runnable hook) {
            this.beforeSendHook = hook;
        }

        void triggerCompletion() {
            if (completionCallback != null) {
                completionCallback.run();
            }
        }

        void triggerTimeout() {
            if (timeoutCallback != null) {
                timeoutCallback.run();
            }
        }

        void triggerError(Throwable ex) {
            if (errorCallback != null) {
                errorCallback.accept(ex);
            } else {
                throw new IllegalStateException("errorCallback nao registrado", ex);
            }
        }

        boolean isCompleted() {
            return completed.get();
        }

        List<String> getRawFrames() {
            return List.copyOf(rawFrames);
        }

        List<MediaType> getDataMediaTypes() {
            return List.copyOf(dataMediaTypes);
        }
    }

    private static List<Cuidador> sementeComPiiSintetica(String statusCarlos, String substituto) {
        return List.of(
                new Cuidador(
                        1L,
                        "Ana Souza",
                        StatusCuidador.PRESENTE,
                        "Diurno (07h-19h)",
                        "Quarto 01",
                        null,
                        12,
                        null),
                new Cuidador(
                        3L,
                        "Carlos Rocha",
                        statusCarlos,
                        "Noturno (19h-07h)",
                        "Quarto 03",
                        "+55 11 90000-0010",
                        8,
                        substituto));
    }

    private static String extrairLinhaData(String rawFrame) {
        for (String linha : rawFrame.split("\n")) {
            if (linha.startsWith("data:")) {
                return linha.substring("data:".length());
            }
        }
        throw new AssertionError("Frame SSE sem campo data: " + rawFrame);
    }

    @Test
    @DisplayName(
            "Envia snapshot inicial SCHEDULE_UPDATED com id, retry:3000 e JSON UTF-8 sem double-quoting nem null (S01, S03, S04)")
    void deveEnviarSnapshotInicialSemDoubleQuotingEOmitindoCamposNulos() throws Exception {
        List<TestSseEmitter> criados = new ArrayList<>();
        CuidadorSseBroadcaster hub =
                new CuidadorSseBroadcaster(
                        JSON_MAPPER,
                        30_000L,
                        10,
                        3_000L,
                        0L,
                        Runnable::run,
                        timeout -> {
                            TestSseEmitter e = new TestSseEmitter(timeout);
                            criados.add(e);
                            return e;
                        },
                        LgpdPrivacyMasker::projetarListaPublica);

        CuidadorSseBroadcaster.SnapshotEscala snapInicial =
                new CuidadorSseBroadcaster.SnapshotEscala(
                        1L, sementeComPiiSintetica(StatusCuidador.PRESENTE, null));

        SseEmitter retornado = hub.registrarAssinante(snapInicial, false);
        assertSame(criados.get(0), retornado);
        assertEquals(30_000L, criados.get(0).getTimeout());
        assertEquals(1, hub.contarAssinantesAtivos());
        assertEquals(1, hub.contarOrdemChegadaRegistrada());

        List<String> frames = criados.get(0).getRawFrames();
        assertEquals(1, frames.size());
        String frame = frames.get(0);
        assertTrue(frame.contains("id:1\n"));
        assertTrue(frame.contains("event:SCHEDULE_UPDATED\n"));
        assertTrue(frame.contains("retry:3000\n"));

        String jsonData = extrairLinhaData(frame);
        assertTrue(jsonData.startsWith("[{"));
        assertFalse(jsonData.startsWith("\"["));
        assertFalse(jsonData.contains("null"));
        assertFalse(jsonData.contains("\"substituto\""));
        assertEquals(
                new MediaType("text", "plain", StandardCharsets.UTF_8),
                criados.get(0).getDataMediaTypes().get(0));

        List<Cuidador> desserializados =
                JSON_MAPPER.readValue(jsonData, new TypeReference<List<Cuidador>>() {});
        assertEquals(2, desserializados.size());
        assertEquals("Carlos Rocha", desserializados.get(1).getNome());
        assertEquals("+55 11 90000-0010", desserializados.get(1).getTelefone());
    }

    @Test
    @DisplayName(
            "Isola canais simultaneos privacy=false e privacy=true com projecao LGPD preguicosa unica (S07, S08, M5.2)")
    void deveIsolarCanaisLgpdEProjetarListaPublicaSomenteQuandoNecessario() throws Exception {
        List<TestSseEmitter> criados = new ArrayList<>();
        AtomicInteger chamadasLgpd = new AtomicInteger(0);

        CuidadorSseBroadcaster hub =
                new CuidadorSseBroadcaster(
                        JSON_MAPPER,
                        30_000L,
                        10,
                        3_000L,
                        0L,
                        Runnable::run,
                        timeout -> {
                            TestSseEmitter e = new TestSseEmitter(timeout);
                            criados.add(e);
                            return e;
                        },
                        lista -> {
                            chamadasLgpd.incrementAndGet();
                            return LgpdPrivacyMasker.projetarListaPublica(lista);
                        });

        CuidadorSseBroadcaster.SnapshotEscala v1 =
                new CuidadorSseBroadcaster.SnapshotEscala(
                        1L, sementeComPiiSintetica(StatusCuidador.PRESENTE, null));

        // Dois assinantes canonicos (privacy=false) -> 0 invocacoes de LgpdPrivacyMasker
        hub.registrarAssinante(v1, false);
        hub.registrarAssinante(v1, false);
        assertEquals(0, chamadasLgpd.get());

        CuidadorSseBroadcaster.SnapshotEscala v2 =
                new CuidadorSseBroadcaster.SnapshotEscala(
                        2L, sementeComPiiSintetica(StatusCuidador.FALTOU, null));
        hub.publicarAtualizacao(v2);
        assertEquals(0, chamadasLgpd.get());

        // Agora entram DOIS assinantes em modo mural publico (privacy=true)
        hub.registrarAssinante(v2, true);
        hub.registrarAssinante(v2, true);
        assertEquals(2, chamadasLgpd.get());
        assertEquals(2, hub.contarAssinantesPrivacidadeAtivos());

        // Em um broadcast para 4 clientes (2 false + 2 true), a projecao LGPD roda APENAS 1 vez!
        chamadasLgpd.set(0);
        CuidadorSseBroadcaster.SnapshotEscala v3 =
                new CuidadorSseBroadcaster.SnapshotEscala(
                        3L, sementeComPiiSintetica(StatusCuidador.COBERTURA, "João da Conceição"));
        hub.publicarAtualizacao(v3);
        assertEquals(1, chamadasLgpd.get());

        // Clientes canonicos recebem dados completos
        String jsonCanonico = extrairLinhaData(criados.get(0).getRawFrames().get(2));
        assertTrue(jsonCanonico.contains("Carlos Rocha"));
        assertTrue(jsonCanonico.contains("João da Conceição"));
        assertTrue(jsonCanonico.contains("+55 11 90000-0010"));

        // Clientes privacy=true recebem nomes abreviados e zero digitos no telefone
        for (int idx : new int[] {2, 3}) {
            List<String> framesMural = criados.get(idx).getRawFrames();
            for (String f : framesMural) {
                String jsonPublico = extrairLinhaData(f);
                assertFalse(jsonPublico.contains("Rocha"));
                assertFalse(jsonPublico.contains("Conceição"));
                assertFalse(jsonPublico.contains("90000"));
                List<Cuidador> lidos =
                        JSON_MAPPER.readValue(jsonPublico, new TypeReference<List<Cuidador>>() {});
                assertEquals("Carlos R.", lidos.get(1).getNome());
                assertEquals("(••) •••••-••••", lidos.get(1).getTelefone());
                assertTrue(
                        lidos.get(1).getTelefone().chars().noneMatch(ch -> ch >= '0' && ch <= '9'));
                assertEquals(8, lidos.get(1).getHorasDescanso());
            }
            List<Cuidador> v3Publico =
                    JSON_MAPPER.readValue(
                            extrairLinhaData(framesMural.get(1)),
                            new TypeReference<List<Cuidador>>() {});
            assertEquals("João C.", v3Publico.get(1).getSubstituto());
        }
    }

    @Test
    @DisplayName(
            "Isola IOException de cliente morto sem interromper demais assinantes e limpa onCompletion/onTimeout/onError + ordemChegada (S11, S12)")
    void deveIsolarFalhaDeRedeELimparRecursosSemVazarOrdemChegada() {
        List<TestSseEmitter> criados = new ArrayList<>();
        CuidadorSseBroadcaster hub =
                new CuidadorSseBroadcaster(
                        JSON_MAPPER,
                        30_000L,
                        10,
                        3_000L,
                        0L,
                        Runnable::run,
                        timeout -> {
                            TestSseEmitter e = new TestSseEmitter(timeout);
                            criados.add(e);
                            return e;
                        },
                        LgpdPrivacyMasker::projetarListaPublica);

        CuidadorSseBroadcaster.SnapshotEscala v1 =
                new CuidadorSseBroadcaster.SnapshotEscala(
                        1L, sementeComPiiSintetica(StatusCuidador.PRESENTE, null));

        // 1) Falha ja no envio inicial: assinante nasce removido
        CuidadorSseBroadcaster hubFalhaInicial =
                new CuidadorSseBroadcaster(
                        JSON_MAPPER,
                        30_000L,
                        10,
                        3_000L,
                        0L,
                        Runnable::run,
                        timeout -> {
                            TestSseEmitter quebrado = new TestSseEmitter(timeout);
                            quebrado.failNextSend("Socket fechado na abertura");
                            return quebrado;
                        },
                        LgpdPrivacyMasker::projetarListaPublica);
        hubFalhaInicial.registrarAssinante(v1, true);
        assertEquals(0, hubFalhaInicial.contarAssinantesAtivos());
        assertEquals(0, hubFalhaInicial.contarOrdemChegadaRegistrada());
        assertEquals(0, hubFalhaInicial.contarAssinantesPrivacidadeAtivos());
        assertTrue(hubFalhaInicial.obterFalhasTransporteDescartadas() >= 1L);

        // 2) Quatro assinantes saudaveis: testamos IOException no #0, onCompletion no #1,
        // onTimeout no #2 e entregamos normalmente ao #3
        for (int i = 0; i < 4; i++) {
            hub.registrarAssinante(v1, i % 2 == 1);
        }
        assertEquals(4, hub.contarAssinantesAtivos());
        assertEquals(4, hub.contarOrdemChegadaRegistrada());

        criados.get(0).failNextSend("Broken pipe no cliente 0");
        CuidadorSseBroadcaster.SnapshotEscala v2 =
                new CuidadorSseBroadcaster.SnapshotEscala(
                        2L, sementeComPiiSintetica(StatusCuidador.FALTOU, null));
        hub.publicarAtualizacao(v2);

        // Cliente 0 foi purgado; clientes 1, 2 e 3 receberam v2 intactos!
        assertEquals(3, hub.contarAssinantesAtivos());
        assertEquals(3, hub.contarOrdemChegadaRegistrada());
        assertTrue(criados.get(0).isCompleted());
        assertEquals(2, criados.get(1).getRawFrames().size());
        assertEquals(2, criados.get(2).getRawFrames().size());
        assertEquals(2, criados.get(3).getRawFrames().size());

        // Desconexoes explicitas via callbacks do container Servlet
        criados.get(1).triggerCompletion();
        assertEquals(2, hub.contarAssinantesAtivos());
        assertEquals(2, hub.contarOrdemChegadaRegistrada());

        criados.get(2).triggerTimeout();
        assertTrue(criados.get(2).isCompleted());
        assertEquals(1, hub.contarAssinantesAtivos());
        assertEquals(1, hub.contarOrdemChegadaRegistrada());

        criados.get(3).triggerError(new IOException("Connection reset by peer"));
        assertTrue(criados.get(3).isCompleted());
        assertEquals(0, hub.contarAssinantesAtivos());
        assertEquals(0, hub.contarOrdemChegadaRegistrada());
        assertEquals(0, hub.contarAssinantesPrivacidadeAtivos());
    }

    @Test
    @DisplayName(
            "Garante ordem monotonica estrita (descarta v2 <= v3), fecha janela de corrida no registro e aplica eviccao FIFO em maxSubscribers (S13, S14, S16)")
    void deveGarantirOrdemMonotonicaJanelaDeRegistroFifoEHeartbeat() {
        List<TestSseEmitter> criados = new ArrayList<>();
        CuidadorSseBroadcaster hub =
                new CuidadorSseBroadcaster(
                        JSON_MAPPER,
                        30_000L,
                        2,
                        3_000L,
                        0L,
                        Runnable::run,
                        timeout -> {
                            TestSseEmitter e = new TestSseEmitter(timeout);
                            criados.add(e);
                            return e;
                        },
                        LgpdPrivacyMasker::projetarListaPublica);

        CuidadorSseBroadcaster.SnapshotEscala v1 =
                new CuidadorSseBroadcaster.SnapshotEscala(
                        1L, sementeComPiiSintetica(StatusCuidador.PRESENTE, null));
        CuidadorSseBroadcaster.SnapshotEscala v2 =
                new CuidadorSseBroadcaster.SnapshotEscala(
                        2L, sementeComPiiSintetica(StatusCuidador.FALTOU, null));
        CuidadorSseBroadcaster.SnapshotEscala v3 =
                new CuidadorSseBroadcaster.SnapshotEscala(
                        3L, sementeComPiiSintetica(StatusCuidador.COBERTURA, "Folguista A"));

        hub.registrarAssinante(v1, false);
        // Publica v3 primeiro e tenta publicar v2 atrasado depois -> v2 deve ser descartado!
        hub.publicarAtualizacao(v3);
        hub.publicarAtualizacao(v2);

        List<String> framesPrimeiro = criados.get(0).getRawFrames();
        assertEquals(2, framesPrimeiro.size());
        assertTrue(framesPrimeiro.get(0).contains("id:1\n"));
        assertTrue(framesPrimeiro.get(1).contains("id:3\n"));

        // Corrida A (M1.2): novo assinante chama registrarAssinante passando v1 desatualizado
        // depois que o hub ja viu v3 -> deve receber imediatamente id:3 (jamais id:1)!
        hub.registrarAssinante(v1, false);
        assertEquals(1, criados.get(1).getRawFrames().size());
        assertTrue(criados.get(1).getRawFrames().get(0).contains("id:3\n"));

        // Teto FIFO (maxSubscribers = 2): registrar 3o cliente evicta o 1o (mais antigo)
        hub.registrarAssinante(v3, true);
        assertEquals(2, hub.contarAssinantesAtivos());
        assertEquals(2, hub.contarOrdemChegadaRegistrada());
        assertTrue(criados.get(0).isCompleted());
        assertFalse(criados.get(1).isCompleted());
        assertFalse(criados.get(2).isCompleted());

        // Heartbeat periodico envia comentario ':heartbeat' e expurga cliente que falhar
        hub.enviarHeartbeatPeriodico();
        assertTrue(
                criados.get(1)
                        .getRawFrames()
                        .get(criados.get(1).getRawFrames().size() - 1)
                        .startsWith(":heartbeat"));

        criados.get(1).failNextSend("Half-open TCP detectado no heartbeat");
        hub.enviarHeartbeatPeriodico();
        assertEquals(1, hub.contarAssinantesAtivos());
        assertEquals(1, hub.contarOrdemChegadaRegistrada());

        // Shutdown limpo via DisposableBean.destroy()
        hub.destroy();
        assertTrue(hub.estaEncerrado());
        assertEquals(0, hub.contarAssinantesAtivos());
        assertEquals(0, hub.contarOrdemChegadaRegistrada());
        assertTrue(criados.get(2).isCompleted());
    }

    @Test
    @DisplayName(
            "Barreira concorrente (registro + broadcasts simultaneos) garante sequencia estritamente crescente sem inversao v2->v1 (M1.2, S14)")
    void deveGarantirLinearizabilidadeSobRegistroEBroadcastConcorrentes() throws Exception {
        List<TestSseEmitter> criados = new CopyOnWriteArrayList<>();
        CuidadorSseBroadcaster hubConcorrente =
                new CuidadorSseBroadcaster(
                        JSON_MAPPER,
                        30_000L,
                        64,
                        3_000L,
                        0L,
                        Runnable::run,
                        timeout -> {
                            TestSseEmitter e = new TestSseEmitter(timeout);
                            criados.add(e);
                            return e;
                        },
                        LgpdPrivacyMasker::projetarListaPublica);

        CuidadorSseBroadcaster.SnapshotEscala baseV1 =
                new CuidadorSseBroadcaster.SnapshotEscala(
                        1L, sementeComPiiSintetica(StatusCuidador.PRESENTE, null));

        final int totalThreads = 16;
        final CyclicBarrier barreira = new CyclicBarrier(totalThreads);
        final ExecutorService pool = Executors.newFixedThreadPool(totalThreads);

        try {
            List<Callable<Void>> tarefas = new ArrayList<>(totalThreads);
            for (int t = 0; t < totalThreads; t++) {
                final int idx = t;
                tarefas.add(
                        () -> {
                            barreira.await(5, TimeUnit.SECONDS);
                            if (idx % 2 == 0) {
                                hubConcorrente.registrarAssinante(baseV1, idx % 4 == 0);
                            } else {
                                for (long seq = 2L + idx; seq <= 40L; seq += 2L) {
                                    hubConcorrente.publicarAtualizacao(
                                            new CuidadorSseBroadcaster.SnapshotEscala(
                                                    seq,
                                                    sementeComPiiSintetica(
                                                            StatusCuidador.FALTOU, null)));
                                }
                            }
                            return null;
                        });
            }

            List<Future<Void>> futuros = new ArrayList<>(totalThreads);
            for (Callable<Void> tarefa : tarefas) {
                futuros.add(pool.submit(tarefa));
            }
            for (Future<Void> f : futuros) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            hubConcorrente.destroy();
        }

        assertEquals(8, criados.size());
        for (TestSseEmitter emitter : criados) {
            List<String> frames = emitter.getRawFrames();
            assertFalse(frames.isEmpty());
            long anterior = 0L;
            for (String frame : frames) {
                String primeiraLinha = frame.split("\n")[0];
                assertTrue(primeiraLinha.startsWith("id:"));
                long seqAtual = Long.parseLong(primeiraLinha.substring("id:".length()));
                assertTrue(
                        seqAtual > anterior,
                        "Ordem monotonica violada: " + seqAtual + " apos " + anterior);
                anterior = seqAtual;
            }
        }
    }

    @Test
    @DisplayName("Valida pre-condicoes fail-fast de configuracao e de SnapshotEscala (M1.1, M4.2)")
    void deveRejeitarParametrosInvalidosNoConstrutorENoSnapshot() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CuidadorSseBroadcaster.SnapshotEscala(
                                0L, sementeComPiiSintetica(StatusCuidador.PRESENTE, null)));
        assertThrows(
                NullPointerException.class,
                () -> new CuidadorSseBroadcaster.SnapshotEscala(1L, null));

        assertThrows(
                IllegalArgumentException.class,
                () -> new CuidadorSseBroadcaster(JSON_MAPPER, 1000L, 0, 3000L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CuidadorSseBroadcaster(JSON_MAPPER, -1L, 10, 3000L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CuidadorSseBroadcaster(JSON_MAPPER, 1000L, 10, -1L, 0L));
    }
}
