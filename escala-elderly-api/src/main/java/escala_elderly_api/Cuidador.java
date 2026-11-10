package escala_elderly_api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modelo de dominio imutavel de um Cuidador alinhado ao Frontend (ADR-BE-003, ADR-BE-004,
 * ADR-BE-010). Campos opcionais nulos sao omitidos no JSON (@JsonInclude NON_NULL) para manter
 * compatibilidade limpa com propriedades opcionais (? no TypeScript).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Cuidador {

    private final Long id;
    private final String nome;
    private final String status;
    private final String turno;
    private final String quarto;
    private final String telefone;
    private final Integer horasDescanso;
    private final String substituto;

    // JEITO MAIS SIMPLES: Construtor enxuto preservado por didatica e compatibilidade (ADR-BE-004)
    public Cuidador(Long idNovo, String nomeNovo, String statusNovo) {
        this(idNovo, nomeNovo, statusNovo, null, null, null, null, null);
    }

    // Construtor pratico para a semente inicial da escala (ADR-BE-010)
    public Cuidador(
            Long idNovo, String nomeNovo, String statusNovo, String turnoNovo, String quartoNovo) {
        this(idNovo, nomeNovo, statusNovo, turnoNovo, quartoNovo, null, null, null);
    }

    // Construtor completo usado pelo motor de estado e pelo Jackson (@JsonCreator)
    @JsonCreator
    public Cuidador(
            @JsonProperty("id") Long id,
            @JsonProperty("nome") String nome,
            @JsonProperty("status") String status,
            @JsonProperty("turno") String turno,
            @JsonProperty("quarto") String quarto,
            @JsonProperty("telefone") String telefone,
            @JsonProperty("horasDescanso") Integer horasDescanso,
            @JsonProperty("substituto") String substituto) {
        this.id = id; // id recebe o identificador unico (ex: 3 = Carlos Rocha)
        this.nome = nome; // nome recebe o nome do profissional
        this.status = status; // status recebe Presente, Faltou, Folga ou Cobertura
        this.turno = turno;
        this.quarto = quarto;
        this.telefone = telefone;
        this.horasDescanso = horasDescanso;
        this.substituto = substituto;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getStatus() {
        return status;
    }

    public String getTurno() {
        return turno;
    }

    public String getQuarto() {
        return quarto;
    }

    public String getTelefone() {
        return telefone;
    }

    public Integer getHorasDescanso() {
        return horasDescanso;
    }

    public String getSubstituto() {
        return substituto;
    }
}
