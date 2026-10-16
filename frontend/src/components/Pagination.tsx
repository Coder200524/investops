import React from 'react'

interface PaginationProps {
  currentPage: number
  totalPages: number
  totalElements: number
  pageSize: number
  onPageChange: (newPage: number) => void
}

export const Pagination: React.FC<PaginationProps> = ({
  currentPage,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
}) => {
  if (totalElements === 0) return null

  const start = currentPage * pageSize + 1
  const end = Math.min((currentPage + 1) * pageSize, totalElements)

  return (
    <div className="pagination-container">
      <div className="pagination-info">
        Showing <strong>{start}</strong> to <strong>{end}</strong> of <strong>{totalElements}</strong> entries
      </div>
      <div className="pagination-controls">
        <button
          className="btn btn-secondary btn-sm"
          disabled={currentPage === 0}
          onClick={() => onPageChange(currentPage - 1)}
        >
          Previous
        </button>
        <span className="pagination-page-indicator">
          Page {currentPage + 1} of {Math.max(1, totalPages)}
        </span>
        <button
          className="btn btn-secondary btn-sm"
          disabled={currentPage >= totalPages - 1}
          onClick={() => onPageChange(currentPage + 1)}
        >
          Next
        </button>
      </div>
    </div>
  )
}
