# WordCount — Manual do Usuário

Este manual descreve como preparar o ambiente, compilar, executar os benchmarks e gerar os gráficos. Instruções focadas em Windows (PowerShell) com notas para Unix/macOS quando relevante.

## Pré-requisitos
- Java JDK 8+ (verifique com `java -version` e `javac -version`).
- Python 3.8+ (opcional, necessário para gerar gráficos).
- (Opcional, para GPU) `jocl-2.0.4.jar` em `wordcount/libs/` e as bibliotecas nativas do JOCL (DLL/.so/.dylib) disponíveis no `PATH` ou definidas via `-Djava.library.path`.

## Preparar ambiente Python (Windows PowerShell)

1. Criar e ativar ambiente virtual:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

2. Instalar dependências:

```powershell
pip install -r requirements.txt
```

## Compilar o projeto (Java)

Abra PowerShell no diretório `wordcount` e rode:

```powershell
cd src
# Se tiver JOCL jar disponível (recomendado para testar GPU):
javac -cp ".;../libs/jocl-2.0.4.jar" WordCountMain.java GPUWordCounter.java

# Se não tiver JOCL e quer apenas testar CPU/serial:
javac WordCountMain.java
cd ..
```

Se receber erro `UnsatisfiedLinkError` ao executar modo GPU, confira as libs nativas do JOCL e adicione-as ao `PATH` ou use `-Djava.library.path` no comando `java`.

## Executar benchmarks

Use o script `benchmark.ps1` para automatizar partições, execução e geração de CSVs (Windows PowerShell):

```powershell
# No diretório wordcount
.\benchmark.ps1
```

O script gera arquivos CSV temporários e um `results/results.csv` consolidado.

## Gerar gráficos

Com o venv ativo e dependências instaladas:

```powershell
python python-frontend/plot_results.py results\results.csv
```

Os gráficos (`results_time.png`, `results_speedup.png`, `results_counts.png`) são salvos em `results/`.

## Troubleshooting rápido

- Se o plot falhar por estilo Matplotlib: verifique versões do Matplotlib ou remova estilos customizados.
- Se o GPU falhar: execute primeiro com `all` mas monitore mensagens de exceção; execute `WordCountMain cpu` para comparar.
- Para problemas de encoding em strings alvo: o kernel GPU compara bytes; evite caracteres multi-byte ou normalize UTF-8 uniformemente.

## Checklist de commit (recomendações)

- Não commite arquivos em `results/`, nem ambientes virtuais, nem artefatos compilados (`*.class`). O `.gitignore` do repositório já cobre isso.
- Documente onde o `jocl-2.0.4.jar` deve ser obtido e como adicionar as libs nativas ao `PATH` no `USER_MANUAL.md`.
- Exemplo de comandos git para commitar:

```powershell
git add src python-frontend README.md USER_MANUAL.md requirements.txt .gitignore benchmark.ps1
git commit -m "Prepare repository for distribution: add docs and ignore rules"
git push
```

## Notas finais

Se quiser, eu posso:
- Gerar um `run.ps1` que passe automaticamente `-Djava.library.path` com um parâmetro.
- Gerar um `build.sh` equivalente para Unix/macOS.
- Adicionar instruções de instalação automática do JOCL (não recomendado sem aprovação).

Escolha uma dessas opções ou peça para eu fazer o commit automaticamente (eu só prepararei os comandos, não executarei o push sem sua confirmação).
