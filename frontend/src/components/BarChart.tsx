import React from 'react'

interface BarItem {
  label: string
  value: number
  color?: string
}

interface BarChartProps {
  data: BarItem[]
  title: string
  maxVal?: number
}

export const BarChart: React.FC<BarChartProps> = ({ data, title, maxVal }) => {
  const calculatedMax = maxVal || Math.max(...data.map((d) => d.value), 1)

  return (
    <div className="bar-chart-card">
      <h3 className="chart-title">{title}</h3>
      <div className="bar-chart-list">
        {data.map((item, idx) => {
          const pct = Math.min(100, Math.round((item.value / calculatedMax) * 100))
          return (
            <div key={idx} className="bar-chart-row">
              <div className="bar-chart-header">
                <span className="bar-label">{item.label}</span>
                <span className="bar-value">{item.value}</span>
              </div>
              <div className="bar-track">
                <div
                  className="bar-fill"
                  style={{
                    width: `${pct}%`,
                    backgroundColor: item.color || 'var(--primary-600)',
                  }}
                />
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
