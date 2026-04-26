// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { CommentMarkdownRenderer } from './comment-markdown-renderer'

describe('CommentMarkdownRenderer XSS regression', () => {
  it('strips raw <script> tags', () => {
    const { container } = render(
      <CommentMarkdownRenderer content={'before<script>window.x=1;</script>after'} />,
    )
    expect(container.querySelector('script')).toBeNull()
    expect(container.textContent).toContain('before')
    expect(container.textContent).toContain('after')
  })

  it('strips onerror handlers from img tags', () => {
    const { container } = render(
      <CommentMarkdownRenderer content={'<img src=x onerror="window.x=1">'} />,
    )
    container.querySelectorAll('img').forEach((img) => {
      expect(img.getAttribute('onerror')).toBeNull()
    })
  })

  it('strips javascript: URLs from links', () => {
    const { container } = render(
      <CommentMarkdownRenderer content={'[click](javascript:alert(1))'} />,
    )
    container.querySelectorAll('a').forEach((a) => {
      const href = a.getAttribute('href') ?? ''
      expect(href.toLowerCase()).not.toContain('javascript:')
    })
  })

  it('renders ordinary markdown headings and lists', () => {
    const { container } = render(
      <CommentMarkdownRenderer content={'## Title\n\n- one\n- two'} />,
    )
    expect(container.querySelector('h2')?.textContent).toBe('Title')
    expect(container.querySelectorAll('li')).toHaveLength(2)
  })
})
