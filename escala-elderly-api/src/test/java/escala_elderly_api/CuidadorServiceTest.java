package escala_elderly_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CuidadorServiceTest {

    private CuidadorService service;

    @BeforeEach
    void setUp() {
        service = new CuidadorService();
    }

    @Test
    @DisplayName(
            "Semente inicial alinha IDs 1..4 ao Frontend com Carlos Rocha no id=3 e lista imutavel")
    void deveIniciarComSementeCanonicaImutavelAlinhadaAoFrontend() {
        List<Cuidador> lista = service.listarCuidadores();

        assertEquals(4, lista.size());
        assertEquals(1L, lista.get(0).getId());
        assertEquals("Ana Souza", lista.get(0).getNome());
        assertEquals("Presente", lista.get(0).getStatus());
        assertEquals("Diurno (07h-19h)", lista.get(0).getTurno());
        assertEquals("Quarto 01", lista.get(0).getQuarto());
        assertNull(lista.get(0).getSubstituto());

        assertEquals(2L, lista.get(1).getId());
        assertEquals("Lucas Lima", lista.get(1).getNome());

        assertEquals(3L, lista.get(2).getId());
        assertEquals("Carlos Rocha", lista.get(2).getNome());
        assertEquals("Presente", lista.get(2).getStatus());
        assertEquals("Noturno (19h-07h)", lista.get(2).getTurno());
        assertEquals("Quarto 03", lista.get(2).getQuarto());

        assertEquals(4L, lista.get(3).getId());
        assertEquals("Mariana Costa", lista.get(3).getNome());
        assertEquals("Presente", lista.get(3).getStatus());

        assertThrows(UnsupportedOperationException.class, lista::clear);
    }

    @Test
    @DisplayName(
            "atualizarStatus altera Carlos (id=3), persiste substituto em Cobertura e limpa ao voltar a Presente")
    void deveTransicionarStatusELimparSubstitutoForaDeCobertura() {
        Cuidador faltou =
                service.atualizarStatus(3L, new StatusUpdateRequest("Faltou", "Fantasma Ignorado"));
        assertEquals(3L, faltou.getId());
        assertEquals("Carlos Rocha", faltou.getNome());
        assertEquals("Faltou", faltou.getStatus());
        assertNull(faltou.getSubstituto());
        assertEquals("Presente", service.listarCuidadores().get(3).getStatus());

        Cuidador coberto =
                service.atualizarStatus(
                        3L, new StatusUpdateRequest("Cobertura", "  Folguista Turno A  "));
        assertEquals("Cobertura", coberto.getStatus());
        assertEquals("Folguista Turno A", coberto.getSubstituto());
        assertEquals("Folguista Turno A", service.listarCuidadores().get(2).getSubstituto());

        Cuidador presenteNovamente =
                service.atualizarStatus(
                        3L, new StatusUpdateRequest("Presente", "Folguista Turno A"));
        assertEquals("Presente", presenteNovamente.getStatus());
        assertNull(presenteNovamente.getSubstituto());
        assertNull(service.listarCuidadores().get(2).getSubstituto());

        Cuidador coberturaSemNome =
                service.atualizarStatus(3L, new StatusUpdateRequest("Cobertura", "   "));
        assertEquals("Cobertura", coberturaSemNome.getStatus());
        assertNull(coberturaSemNome.getSubstituto());
    }

    @Test
    @DisplayName(
            "atualizarStatus valida 400 antes de 404 e rejeita IDs, status e caracteres invalidos")
    void deveValidarPreCondicoes400AntesDe404EmAtualizarStatus() {
        CuidadorNotFoundException notFound =
                assertThrows(
                        CuidadorNotFoundException.class,
                        () ->
                                service.atualizarStatus(
                                        999L, new StatusUpdateRequest("Folga", null)));
        assertEquals("CUIDADOR_NOT_FOUND", notFound.getCode());
        assertEquals(999L, notFound.getCuidadorId());

        InvalidCuidadorPayloadException badStatusBefore404 =
                assertThrows(
                        InvalidCuidadorPayloadException.class,
                        () ->
                                service.atualizarStatus(
                                        999L, new StatusUpdateRequest("Trabalhando", null)));
        assertEquals("INVALID_STATUS", badStatusBefore404.getCode());

        InvalidCuidadorPayloadException caseMismatch =
                assertThrows(
                        InvalidCuidadorPayloadException.class,
                        () ->
                                service.atualizarStatus(
                                        1L, new StatusUpdateRequest("presente", null)));
        assertEquals("INVALID_STATUS", caseMismatch.getCode());

        InvalidCuidadorPayloadException nullReq =
                assertThrows(
                        InvalidCuidadorPayloadException.class,
                        () -> service.atualizarStatus(1L, null));
        assertEquals("INVALID_PAYLOAD", nullReq.getCode());

        for (Long badId : new Long[] {null, 0L, -5L, CuidadorService.MAX_SAFE_JS_ID + 1L}) {
            InvalidCuidadorPayloadException ex =
                    assertThrows(
                            InvalidCuidadorPayloadException.class,
                            () ->
                                    service.atualizarStatus(
                                            badId, new StatusUpdateRequest("Presente", null)));
            assertEquals("INVALID_ID", ex.getCode());
        }

        InvalidCuidadorPayloadException nullByteEx =
                assertThrows(
                        InvalidCuidadorPayloadException.class,
                        () ->
                                service.atualizarStatus(
                                        1L,
                                        new StatusUpdateRequest(
                                                "Cobertura", "Folguista\u0000Injetado")));
        assertEquals("INVALID_PAYLOAD", nullByteEx.getCode());
    }

    @Test
    @DisplayName(
            "sincronizarLote gera Auto-ID monotonico (maxExplicitId + 1), aplica trimToNull e limpa substituto indevido")
    void deveSincronizarLoteComAutoIdMonotonicoESimetriaDeInvariantes() {
        List<CuidadorBatchItem> csvSemId =
                List.of(
                        new CuidadorBatchItem(
                                null,
                                "  Cuidador Sintetico 01  ",
                                "Presente",
                                "Diurno (07h-19h)",
                                "   ",
                                "+55 11 90000-0001",
                                24,
                                "Substituto Indevido"),
                        new CuidadorBatchItem(
                                null,
                                "Folguista Turno B",
                                "Cobertura",
                                "Noturno (19h-07h)",
                                "Quarto 02",
                                "+55 11 90000-0002",
                                36,
                                "  Folguista Reserva  "));

        List<Cuidador> salvoCsv = service.sincronizarLote(csvSemId);
        assertEquals(2, salvoCsv.size());
        assertEquals(1L, salvoCsv.get(0).getId());
        assertEquals("Cuidador Sintetico 01", salvoCsv.get(0).getNome());
        assertNull(salvoCsv.get(0).getQuarto());
        assertNull(salvoCsv.get(0).getSubstituto());
        assertEquals(24, salvoCsv.get(0).getHorasDescanso());

        assertEquals(2L, salvoCsv.get(1).getId());
        assertEquals("Folguista Reserva", salvoCsv.get(1).getSubstituto());

        // Lote misto com IDs explicitos [2, 4] + novo item sem ID -> deve receber 5 (nao 1!)
        List<CuidadorBatchItem> loteMisto =
                List.of(
                        new CuidadorBatchItem(
                                4L,
                                "Mariana Costa",
                                "Presente",
                                "Noturno (19h-07h)",
                                "Quarto 04",
                                null,
                                null,
                                null),
                        new CuidadorBatchItem(
                                null,
                                "Cuidador Sintetico 05",
                                "Folga",
                                "Diurno (07h-19h)",
                                null,
                                "+55 11 90000-0005",
                                18,
                                null),
                        new CuidadorBatchItem(
                                2L,
                                "Lucas Lima",
                                "Presente",
                                "Diurno (07h-19h)",
                                "Quarto 02",
                                null,
                                null,
                                null));

        List<Cuidador> salvoMisto = service.sincronizarLote(loteMisto);
        assertEquals(List.of(2L, 4L, 5L), salvoMisto.stream().map(Cuidador::getId).toList());
        assertEquals("Cuidador Sintetico 05", salvoMisto.get(2).getNome());
    }

    @Test
    @DisplayName(
            "sincronizarLote preserva 100% do snapshot anterior quando qualquer item viola o contrato (All-or-Nothing)")
    void deveManterSnapshotIntactoQuandoLoteFalhaNoMeio() {
        List<Cuidador> snapshotAntes = service.listarCuidadores();

        List<CuidadorBatchItem> loteComItem3Invalido =
                List.of(
                        new CuidadorBatchItem(
                                1L, "Ana Souza", "Presente", null, null, null, 12, null),
                        new CuidadorBatchItem(
                                2L, "Lucas Lima", "Folga", null, null, null, 24, null),
                        new CuidadorBatchItem(
                                3L, "Carlos Rocha", "AusenteInvalido", null, null, null, 12, null));

        assertThrows(
                InvalidCuidadorPayloadException.class,
                () -> service.sincronizarLote(loteComItem3Invalido));
        assertSame(snapshotAntes, service.listarCuidadores());

        List<CuidadorBatchItem> loteComElementoNulo = new ArrayList<>();
        loteComElementoNulo.add(
                new CuidadorBatchItem(1L, "Ana Souza", "Presente", null, null, null, null, null));
        loteComElementoNulo.add(null);
        assertThrows(
                InvalidCuidadorPayloadException.class,
                () -> service.sincronizarLote(loteComElementoNulo));

        List<CuidadorBatchItem> loteIdDuplicado =
                List.of(
                        new CuidadorBatchItem(
                                1L, "Ana Souza", "Presente", null, null, null, null, null),
                        new CuidadorBatchItem(
                                1L, "Lucas Lima", "Folga", null, null, null, null, null));
        assertThrows(
                InvalidCuidadorPayloadException.class,
                () -> service.sincronizarLote(loteIdDuplicado));

        assertThrows(
                InvalidCuidadorPayloadException.class, () -> service.sincronizarLote(List.of()));
        assertThrows(InvalidCuidadorPayloadException.class, () -> service.sincronizarLote(null));

        List<CuidadorBatchItem> loteAcimaDoLimite =
                Collections.nCopies(
                        CuidadorService.MAX_BATCH_SIZE + 1,
                        new CuidadorBatchItem(
                                null,
                                "Cuidador Sintetico",
                                "Presente",
                                null,
                                null,
                                null,
                                null,
                                null));
        assertThrows(
                InvalidCuidadorPayloadException.class,
                () -> service.sincronizarLote(loteAcimaDoLimite));

        List<CuidadorBatchItem> loteOverflowAutoId =
                List.of(
                        new CuidadorBatchItem(
                                CuidadorService.MAX_SAFE_JS_ID,
                                "Cuidador Limite",
                                "Presente",
                                null,
                                null,
                                null,
                                null,
                                null),
                        new CuidadorBatchItem(
                                null, "Cuidador Estouro", "Folga", null, null, null, null, null));
        InvalidCuidadorPayloadException overflowEx =
                assertThrows(
                        InvalidCuidadorPayloadException.class,
                        () -> service.sincronizarLote(loteOverflowAutoId));
        assertEquals("INVALID_ID", overflowEx.getCode());

        assertSame(snapshotAntes, service.listarCuidadores());
    }

    @Test
    @DisplayName(
            "Concorrencia MVCC com 24 threads em CyclicBarrier garante zero torn reads e invariantes intactos")
    void deveSuportar24ThreadsSimultaneasSemTornReadsOuCorrupcaoDeEstado() throws Exception {
        final int totalThreads = 24;
        final int iteracoesPorThread = 150;
        final CyclicBarrier barreira = new CyclicBarrier(totalThreads);
        final ExecutorService executor = Executors.newFixedThreadPool(totalThreads);

        final List<CuidadorBatchItem> loteCincoCuidadores =
                List.of(
                        new CuidadorBatchItem(
                                1L,
                                "Ana Souza",
                                "Presente",
                                "Diurno (07h-19h)",
                                "Quarto 01",
                                null,
                                12,
                                null),
                        new CuidadorBatchItem(
                                2L,
                                "Lucas Lima",
                                "Presente",
                                "Diurno (07h-19h)",
                                "Quarto 02",
                                null,
                                12,
                                null),
                        new CuidadorBatchItem(
                                3L,
                                "Carlos Rocha",
                                "Cobertura",
                                "Noturno (19h-07h)",
                                "Quarto 03",
                                null,
                                24,
                                "Folguista Turno A"),
                        new CuidadorBatchItem(
                                4L,
                                "Mariana Costa",
                                "Presente",
                                "Noturno (19h-07h)",
                                "Quarto 04",
                                null,
                                12,
                                null),
                        new CuidadorBatchItem(
                                5L,
                                "Folguista Turno A",
                                "Folga",
                                "Noturno (19h-07h)",
                                "Reserva",
                                "+55 11 90000-0005",
                                36,
                                null));

        try {
            List<Callable<Void>> tarefas = new ArrayList<>(totalThreads);

            for (int t = 0; t < totalThreads; t++) {
                final int threadIdx = t;
                tarefas.add(
                        () -> {
                            barreira.await(5, TimeUnit.SECONDS);
                            for (int i = 0; i < iteracoesPorThread; i++) {
                                if (threadIdx % 2 == 0) {
                                    List<Cuidador> lido = service.listarCuidadores();
                                    assertNotNull(lido);
                                    assertTrue(
                                            Set.of(4, 5).contains(lido.size()),
                                            "Tamanho inesperado em leitura concorrente: "
                                                    + lido.size());
                                    assertThrows(UnsupportedOperationException.class, lido::clear);

                                    long idAnterior = 0L;
                                    for (Cuidador c : lido) {
                                        assertNotNull(c);
                                        assertTrue(
                                                c.getId() > idAnterior,
                                                "IDs fora de ordem estrita");
                                        idAnterior = c.getId();
                                        assertTrue(StatusCuidador.ehValido(c.getStatus()));
                                        if (!StatusCuidador.COBERTURA.equals(c.getStatus())) {
                                            assertNull(c.getSubstituto());
                                        }
                                    }
                                } else {
                                    int acao = (threadIdx + i) % 4;
                                    if (acao == 0) {
                                        service.atualizarStatus(
                                                3L, new StatusUpdateRequest("Faltou", null));
                                    } else if (acao == 1) {
                                        service.atualizarStatus(
                                                3L,
                                                new StatusUpdateRequest(
                                                        "Cobertura", "Folguista Turno A"));
                                    } else if (acao == 2) {
                                        service.sincronizarLote(loteCincoCuidadores);
                                    } else {
                                        service.restaurarEscalaInicial();
                                    }
                                }
                            }
                            return null;
                        });
            }

            List<Future<Void>> futuros = new ArrayList<>(totalThreads);
            for (Callable<Void> tarefa : tarefas) {
                futuros.add(executor.submit(tarefa));
            }
            for (Future<Void> futuro : futuros) {
                futuro.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        List<Cuidador> restaurado = service.restaurarEscalaInicial();
        assertEquals(4, restaurado.size());
        assertEquals("Carlos Rocha", restaurado.get(2).getNome());
        assertEquals("Presente", restaurado.get(2).getStatus());
    }
}
