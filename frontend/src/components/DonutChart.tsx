import React from 'react'

interface DonutSegment {
  label: string
  value: number
  color: string
}

interface DonutChartProps {
  data: DonutSegment[]
  title: string
  centerLabel?: string
  size?: number
}

export const DonutChart: React.FC<DonutChartProps> = ({
  data,
  title,
  centerLabel,
  size = 180,
}) => {
  const total = data.reduce((acc, curr) => acc + curr.value, 0)
  const radius = 65
  const strokeWidth = 24
  const circumference = 2 * Math.PI * radius

  let currentOffset = 0
  const segments = data.map((item) => {
    const fraction = total > 0 ? item.value / total : 0
    const strokeDasharray = `${fraction * circumference} ${circumference}`
    const strokeDashoffset = -currentOffset
    currentOffset += fraction * circumference
    const percentage = total > 0 ? ((item.value / total) * 100).toFixed(1) : '0'

    return {
      ...item,
      strokeDasharray,
      strokeDashoffset,
      percentage,
    }
  })

  return (
    <div className="donut-chart-card">
      <h3 className="chart-title">{title}</h3>
      <div className="donut-chart-container">
        <svg width={size} height={size} viewBox="0 0 180 180" className="donut-svg">
          <circle
            cx="90"
            cy="90"
            r={radius}
            fill="transparent"
            stroke="var(--border-subtle)"
            strokeWidth={strokeWidth}
          />
          {total > 0 &&
            segments.map((seg, idx) => (
              <circle
                key={idx}
                cx="90"
                cy="90"
                r={radius}
                fill="transparent"
                stroke={seg.color}
                strokeWidth={strokeWidth}
                strokeDasharray={seg.strokeDasharray}
                strokeDashoffset={seg.strokeDashoffset}
                strokeLinecap="round"
                transform="rotate(-90 90 90)"
                className="donut-segment"
              >
                <title>{`${seg.label}: ${seg.value} (${seg.percentage}%)`}</title>
              </circle>
            ))}
          <text x="90" y="85" textAnchor="middle" className="donut-center-total">
            {total}
          </text>
          <text x="90" y="105" textAnchor="middle" className="donut-center-subtext">
            {centerLabel || 'Total'}
          </text>
        </svg>

        <div className="donut-legend">
          {data.map((item, idx) => (
            <div key={idx} className="legend-item">
              <span className="legend-color-dot" style={{ backgroundColor: item.color }} />
              <span className="legend-label">{item.label}</span>
              <span className="legend-count">{item.value}</span>
              <span className="legend-pct">
                ({total > 0 ? ((item.value / total) * 100).toFixed(0) : 0}%)
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
