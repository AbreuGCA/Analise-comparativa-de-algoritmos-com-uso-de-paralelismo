"""
plot_results.py
Usage: python plot_results.py path/to/results.csv

Generates improved visuals comparing mean times (with std error bars), speedup vs SerialCPU,
and counts per method. Saves PNGs into the CSV parent directory (usually `results/`).
"""

import sys
import os
import math
import pandas as pd
import matplotlib.pyplot as plt


def _annotate_bars(ax, fmt="{:.1f}"):
    for p in ax.patches:
        height = p.get_height()
        if math.isfinite(height) and height != 0:
            ax.annotate(fmt.format(height),
                        (p.get_x() + p.get_width() / 2., height),
                        ha='center', va='bottom', fontsize=8, rotation=0)


def plot(csv_path):
    df = pd.read_csv(csv_path)
    # normalize method order if present
    method_order = ['SerialCPU', 'ParallelCPU', 'ParallelGPU']
    df['method'] = pd.Categorical(df['method'], categories=[m for m in method_order if m in df['method'].unique()], ordered=True)

    # include observed=False to avoid FutureWarning about categorical grouping
    agg_time = df.groupby(['file', 'method'], observed=False)['millis'].agg(['mean', 'std', 'count']).reset_index()
    agg_counts = df.groupby(['file', 'method'], observed=False)['count'].agg(['mean', 'std']).reset_index()

    out_dir = os.path.dirname(os.path.abspath(csv_path))
    if out_dir == '':
        out_dir = os.getcwd()

    # Pivot for plotting
    pivot_mean = agg_time.pivot(index='file', columns='method', values='mean')
    pivot_std = agg_time.pivot(index='file', columns='method', values='std')

    files = pivot_mean.index.tolist()
    methods = pivot_mean.columns.tolist()

    # choose a plotting style from preferred list that is available
    preferred_styles = ['seaborn-colorblind', 'seaborn', 'seaborn-darkgrid', 'ggplot', 'classic']
    for s in preferred_styles:
        if s in plt.style.available:
            plt.style.use(s)
            break

    # --- Mean time with error bars ---
    fig, ax = plt.subplots(figsize=(12, 6))
    x = range(len(files))
    total_width = 0.8
    n = len(methods)
    if n == 0:
        print('No methods found in CSV.')
        return
    width = total_width / n

    offsets = [(-total_width/2) + (i + 0.5) * width for i in range(n)]

    colors = plt.rcParams['axes.prop_cycle'].by_key().get('color')

    for i, m in enumerate(methods):
        means = pivot_mean[m].fillna(0).values
        errs = pivot_std[m].fillna(0).values
        ax.bar([xi + offsets[i] for xi in x], means, width=width, label=m, yerr=errs, capsize=4, color=colors[i % len(colors)])

    ax.set_xticks(x)
    ax.set_xticklabels(files, rotation=20, ha='right')
    ax.set_ylabel('Tempo médio (ms)')
    ax.set_title('Tempo médio por método e arquivo (com std)')
    ax.legend()
    ax.grid(axis='y', linestyle='--', alpha=0.5)
    plt.tight_layout()

    out_time = os.path.join(out_dir, 'results_time.png')
    fig.savefig(out_time)
    print(f'Gráfico salvo em: {out_time}')

    _annotate_bars(ax, fmt="{:.1f}")
    fig.savefig(os.path.join(out_dir, 'results_time_annotated.png'))

    plt.close(fig)

    # --- Speedup plot (vs SerialCPU) ---
    # compute baseline per file (SerialCPU) and calculate baseline / method so that >1 means faster than serial
    baseline = pivot_mean.get('SerialCPU')
    if baseline is not None:
        # speedup = SerialCPU_time / method_time
        speedup = pivot_mean.copy()
        for col in pivot_mean.columns:
            speedup[col] = baseline / pivot_mean[col]

        fig2, ax2 = plt.subplots(figsize=(12, 6))
        for i, m in enumerate(methods):
            vals = speedup[m].replace([float('inf'), float('nan')], 0).fillna(0).values
            ax2.bar([xi + offsets[i] for xi in x], vals, width=width, label=m, color=colors[i % len(colors)])

        ax2.set_xticks(x)
        ax2.set_xticklabels(files, rotation=20, ha='right')
        ax2.set_ylabel('Speedup (SerialCPU / method)')
        ax2.set_title('Speedup relativo ao SerialCPU (maior é melhor)')
        ax2.legend()
        ax2.grid(axis='y', linestyle='--', alpha=0.5)
        plt.tight_layout()
        out_speed = os.path.join(out_dir, 'results_speedup.png')
        fig2.savefig(out_speed)
        _annotate_bars(ax2, fmt="{:.2f}")
        fig2.savefig(os.path.join(out_dir, 'results_speedup_annotated.png'))
        plt.close(fig2)
        print(f'Gráfico de speedup salvo em: {out_speed}')
    else:
        print('SerialCPU baseline not found; pulando gráfico de speedup.')

    # --- Counts (sanity check) ---
    pivot_counts = agg_counts.pivot(index='file', columns='method', values='mean')
    fig3, ax3 = plt.subplots(figsize=(12, 6))
    for i, m in enumerate(pivot_counts.columns.tolist()):
        vals = pivot_counts[m].fillna(0).values
        ax3.bar([xi + offsets[i] for xi in x], vals, width=width, label=m, color=colors[i % len(colors)])

    ax3.set_xticks(x)
    ax3.set_xticklabels(files, rotation=20, ha='right')
    ax3.set_ylabel('Contagem média')
    ax3.set_title('Contagem média por método e arquivo')
    ax3.legend()
    ax3.grid(axis='y', linestyle='--', alpha=0.5)
    plt.tight_layout()
    out_counts = os.path.join(out_dir, 'results_counts.png')
    fig3.savefig(out_counts)
    _annotate_bars(ax3, fmt="{:.1f}")
    fig3.savefig(os.path.join(out_dir, 'results_counts_annotated.png'))
    plt.close(fig3)
    print(f'Gráfico de contagens salvo em: {out_counts}')


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python plot_results.py results.csv")
    else:
        plot(sys.argv[1])
