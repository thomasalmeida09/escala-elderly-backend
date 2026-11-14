package escala_elderly_api;

public class Cuidador {
    
    private Long id;
    private String nome;
    private String status;

    // JEITO MAIS SIMPLES: Nomes diferentes no parêntese
    public Cuidador(Long idNovo, String nomeNovo, String statusNovo) {
        id = idNovo;         // id recebe idNovo
        nome = nomeNovo;     // nome recebe nomeNovo
        status = statusNovo; // status recebe statusNovo
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getStatus() { return status; }
}
