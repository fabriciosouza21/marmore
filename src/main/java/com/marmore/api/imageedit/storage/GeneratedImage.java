package com.marmore.api.imageedit.storage;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadados de uma imagem gerada com sucesso: quando foi criada, quanto custou, com qual modelo e
 * onde os bytes ficam no object storage ({@code objetoKey}). Os bytes em si ficam apenas no
 * storage; aqui ficam so os dados da geracao.
 */
@Entity
@Table(name = "generated_image")
public class GeneratedImage {

  @Id @GeneratedValue private UUID id;

  /** Momento da gravacao do metadado. */
  private Instant criadoEm;

  /** Latencia da geracao (chamada ao gateway), em milissegundos. */
  private long latenciaMs;

  /** Custo da geracao em BRL; nulo quando nao foi possivel calcular. */
  private BigDecimal custoBrl;

  /** Modelo usado na geracao. */
  private String modelo;

  /** Key do objeto no storage onde os bytes da imagem estao gravados. */
  private String objetoKey;

  /** Id da pedra escolhida no catalogo no momento da geracao. */
  private String pedra;

  /** Construtor vazio exigido pela JPA; campos preenchidos via setters. */
  public GeneratedImage() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public void setCriadoEm(Instant criadoEm) {
    this.criadoEm = criadoEm;
  }

  public long getLatenciaMs() {
    return latenciaMs;
  }

  public void setLatenciaMs(long latenciaMs) {
    this.latenciaMs = latenciaMs;
  }

  public BigDecimal getCustoBrl() {
    return custoBrl;
  }

  public void setCustoBrl(BigDecimal custoBrl) {
    this.custoBrl = custoBrl;
  }

  public String getModelo() {
    return modelo;
  }

  public void setModelo(String modelo) {
    this.modelo = modelo;
  }

  public String getObjetoKey() {
    return objetoKey;
  }

  public void setObjetoKey(String objetoKey) {
    this.objetoKey = objetoKey;
  }

  public String getPedra() {
    return pedra;
  }

  public void setPedra(String pedra) {
    this.pedra = pedra;
  }
}
