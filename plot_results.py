import pandas as pd
import matplotlib.pyplot as plt

# Đọc dữ liệu
df = pd.read_csv('benchmark_results.csv')

# Tạo biểu đồ so sánh HUI trước và sau khi giấu
fig, ax1 = plt.subplots(figsize=(10, 6))

df.plot(x='Dataset', y=['Original_HUIs', 'Sanitized_HUIs'], kind='bar', ax=ax1)
ax1.set_title('HUI Count: Original vs Sanitized')
ax1.set_ylabel('Number of Itemsets')
plt.xticks(rotation=45)
plt.grid(axis='y', linestyle='--', alpha=0.7)
plt.savefig('hui_comparison.png')

# Tạo biểu đồ Thời gian thực thi
plt.figure(figsize=(10, 6))
plt.bar(df['Dataset'], df['Time_ms'], color='orange')
plt.title('Execution Time across Datasets')
plt.ylabel('Time (ms)')
plt.savefig('execution_time.png')

print('Charts saved as hui_comparison.png and execution_time.png')
