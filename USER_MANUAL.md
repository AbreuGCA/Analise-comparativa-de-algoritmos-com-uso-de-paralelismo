# WordCount — Manual do Usuário

Este manual descreve como preparar o ambiente, compilar, executar os benchmarks e gerar os gráficos. Instruções focadas em Windows (PowerShell) com notas para Unix/macOS quando relevante.

## Pré-requisitos
- Java JDK 8+ (verifique com `java -version` e `javac -version`).
- Python 3.8+ (opcional, necessário para gerar gráficos).
- (Opcional, para GPU) `jocl-2.0.4.jar` em `wordcount/libs/` e as bibliotecas nativas do JOCL (DLL/.so/.dylib) disponíveis no `PATH` ou definidas via `-Djava.library.path`.

## Executar benchmarks

Use o script `benchmark.ps1` para automatizar partições, execução e geração de CSVs (Windows PowerShell):

```powershell
.\benchmark.ps1
```

Caso dê erro de permissão, rode:

```
powershell -ExecutionPolicy Bypass -File 'caminho do projeto\benchmark.ps1
```
O script gera arquivos CSV temporários e um `results/results.csv` consolidado.

## Preparar ambiente Python (Windows PowerShell) - Opcional, o benchmark.ps1 já faz isso

1. Criar e ativar ambiente virtual:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

2. Instalar dependências:

```powershell
pip install -r requirements.txt
```

## Compilar o projeto (Java) - Opcional, o benchmark.ps1 já faz isso

Abra PowerShell no diretório `wordcount` e rode:

Para compilar com suporte a GPU:
```powershell
cd src
javac -cp ".;../libs/jocl-2.0.4.jar" WordCountMain.java GPUWordCounter.java
```

Se não for usar GPU, pode compilar apenas:
```powershell
javac WordCountMain.java
cd ..
```

Se receber erro `UnsatisfiedLinkError` ao executar modo GPU, confira as libs nativas do JOCL e adicione-as ao `PATH` ou use `-Djava.library.path` no comando `java`.

## Gerar gráficos - Opcional, o benchmark.ps1 já faz isso

Com o venv ativo e dependências instaladas:

```powershell
python python-frontend/plot_results.py results\results.csv
```

Os gráficos (`results_time.png`, `results_speedup.png`, `results_counts.png`) são salvos em `results/`.

## Troubleshooting rápido

- Se o plot falhar por estilo Matplotlib: verifique versões do Matplotlib ou remova estilos customizados.
- Se o GPU falhar: execute primeiro com `all` mas monitore mensagens de exceção; execute `WordCountMain cpu` para comparar.
- Para problemas de encoding em strings alvo: o kernel GPU compara bytes; evite caracteres multi-byte ou normalize UTF-8 uniformemente.
