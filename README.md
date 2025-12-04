# Relatório de Benchmarks - Análise Comparativa de Algoritmos com Paralelismo

**Data:** 29 de Novembro de 2025

---

## Resumo

Este documento apresenta a análise comparativa do desempenho de três implementações para a tarefa de contagem de ocorrências da palavra "the" em textos: implementação serial em CPU (`SerialCPU`), paralelização em CPU (`ParallelCPU`) e implementação via GPU (`ParallelGPU`). Os testes foram realizados em três obras clássicas (Don Quixote, Dracula, Moby Dick) em quatro tamanhos (25%, 50%, 75% e 100%), com 3 repetições por cenário. As métricas principais são tempo de execução (ms) e consistência entre execuções. Conclusão principal: para este problema e tamanhos testados, a implementação serial em CPU apresentou o melhor desempenho e estabilidade; as alternativas paralelas (CPU/GPU) mostraram overheads que penalizam o tempo total.

---

## Introdução

Neste trabalho comparamos três abordagens para a contagem de palavras em textos:

- **SerialCPU**: algoritmo sequencial que percorre o texto e contabiliza ocorrências.
- **ParallelCPU**: versão que segmenta o texto e processa segmentos em paralelo usando múltiplas threads no CPU, com posterior redução das contagens.
- **ParallelGPU**: versão que transfere dados para a GPU e executa kernels OpenCL para contar ocorrências, reduzindo resultados no dispositivo e retornando ao host.

A abordagem do estudo foi empírica: gerar amostras (25/50/75/100% dos arquivos), executar cada método 3 vezes, consolidar tempos e contagens em CSV e gerar gráficos para apoiar a análise. O objetivo é identificar padrões de desempenho e avaliar se a paralelização (CPU/GPU) traz ganhos práticos para este tipo de problema.

## Metodologia

Descrição resumida da metodologia e análise estatística aplicada aos resultados:

- Recolha de dados: para cada arquivo (Don Quixote, Dracula, Moby Dick) e para cada percentual (25,50,75,100), executou-se cada método 3 vezes. Os resultados (contagem de ocorrências e tempo em ms) foram consolidados em `results/results.csv`.
- Métricas principais: média de tempo por cenário, variância/coeficiente de variação (CV) entre repetições, e cálculo de speedup relativo (Speedup = TempoSerial / TempoMétodo).
- Visualizações: gráficos de tempo, speedup e verificação de contagens (para detector de erros de implementação).
- Observações experimentais: atenção ao comportamento da primeira execução da GPU (warm-up) e à influência do overhead de criação de threads/transferência de dados.

Configuração dos testes (resumo):

- Palavra-alvo: `the`
- Arquivos de entrada: Don Quixote (388.208 bytes), Dracula (165.307 bytes), Moby Dick (217.452 bytes)
- Tamanhos testados: 25%, 50%, 75%, 100%
- Execuções: 3 repetições por cenário
- Ferramentas: Java para execução dos algoritmos; Python (pandas/matplotlib) para plotagem

## Resultados e Discussão

Os resultados brutos foram consolidados em `results/results.csv`. Abaixo seguem tabelas sumarizadas com os tempos médios por método e percentual, seguidas de observações e gráficos.

### Resultados Agregados — Tempo médio (ms)

#### Don Quixote

| Percentual | SerialCPU | ParallelCPU | ParallelGPU |
| ---------- | --------: | ----------: | ----------: |
| 25%        |      2.00 |       12.67 |      356.33 |
| 50%        |      3.33 |       11.33 |      156.00 |
| 75%        |      5.00 |       13.33 |      166.00 |
| 100%       |      4.67 |       11.67 |      161.33 |

#### Dracula

| Percentual | SerialCPU | ParallelCPU | ParallelGPU |
| ---------- | --------: | ----------: | ----------: |
| 25%        |      1.00 |        7.33 |      155.00 |
| 50%        |      2.67 |        9.33 |      163.00 |
| 75%        |      1.67 |        9.33 |      153.67 |
| 100%       |      3.00 |        9.00 |      153.33 |

#### Moby Dick

| Percentual | SerialCPU | ParallelCPU | ParallelGPU |
| ---------- | --------: | ----------: | ----------: |
| 25%        |      2.00 |        9.00 |      154.00 |
| 50%        |      2.33 |        8.00 |      154.33 |
| 75%        |      2.67 |        9.00 |      156.33 |
| 100%       |      3.33 |        9.00 |      152.00 |

### Discussão

- Em todas as instâncias testadas, a implementação `SerialCPU` foi a mais rápida e mais estável.
- `ParallelCPU` mostra tempos maiores na média, indicando que o overhead de paralelização (criação/gerenciamento de threads, redução das contagens) não foi amortizado pelos tamanhos de entrada usados.
- `ParallelGPU` apresentou tempos significativamente maiores, sobretudo na primeira execução de cada cenário (warm-up/initialization). Isso sugere que o custo de transferência de dados entre host e dispositivo e a inicialização do kernel dominam o tempo total para esta tarefa.
- A variabilidade das medições (coeficiente de variação) é pequena no Serial, moderada no ParallelCPU e alta no ParallelGPU (especialmente na primeira execução).

Estas observações são suportadas pelos gráficos abaixo.

### Gráficos das execuções (visualização)

Os gráficos gerados automaticamente foram embutidos para inspeção:

![Tempo de execução - comparativo](https://i.imgur.com/YetlqIl.png)

![Contagens (verificação de correção)](https://i.imgur.com/hLbvBuz.png)

![Speedup anotado](https://i.imgur.com/Hevqzkl.png)

## Conclusão

Para a tarefa e volumes testados, a implementação serial em CPU fornece melhor desempenho e previsibilidade. As alternativas paralelas (ParallelCPU, ParallelGPU) introduzem overheads (threads, transferências, inicialização de kernels) que não são compensados pelos benefícios de paralelismo neste caso.

Recomendações:

- Use implementação serial para entradas pequenas/moderadas.
- Caso precise de paralelismo, avalie aumentar muito o tamanho dos dados, aumentar a granularidade das tarefas, ou mudar o tipo de problema para um mais compute-bound.

## Anexos

- Link do repositório: https://github.com/AbreuGCA/Analise-comparativa-de-algoritmos-com-uso-de-paralelismo
- Dados consolidados: `results/results.csv`

---
