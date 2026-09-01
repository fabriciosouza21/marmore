package com.marmore.api.imageedit.web;

/**
 * Resumo de uma pedra do catalogo no contrato do {@code GET /pedras}: id, nome comercial e
 * categoria, sem o campo interno {@code arquivo} (caminho da imagem no disco).
 */
record PedraSummary(String id, String nome, String categoria) {}
