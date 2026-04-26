// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react'
import { CommentComposer } from './comment-composer'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

afterEach(() => cleanup())

function getSubmit() {
  return screen.getByText('comments.composer.submit') as HTMLButtonElement
}

describe('CommentComposer', () => {
  it('disables submit when body is empty', () => {
    render(<CommentComposer onSubmit={vi.fn()} isSubmitting={false} />)
    expect(getSubmit().disabled).toBe(true)
  })

  it('enables submit when body has content', () => {
    render(<CommentComposer onSubmit={vi.fn()} isSubmitting={false} />)
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'hello' } })
    expect(getSubmit().disabled).toBe(false)
  })

  it('blocks submit when body exceeds 8192 characters and shows error', () => {
    render(<CommentComposer onSubmit={vi.fn()} isSubmitting={false} />)
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'a'.repeat(8193) } })
    expect(getSubmit().disabled).toBe(true)
    expect(screen.getByText('comments.error.tooLong')).toBeTruthy()
  })

  it('disables submit while isSubmitting is true', () => {
    render(<CommentComposer onSubmit={vi.fn()} isSubmitting={true} />)
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'hello' } })
    expect(getSubmit().disabled).toBe(true)
  })

  it('calls onSubmit with trimmed body and clears textarea on success', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<CommentComposer onSubmit={onSubmit} isSubmitting={false} />)
    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: '  hi  ' } })
    fireEvent.click(getSubmit())
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('hi'))
    await waitFor(() => expect(textarea.value).toBe(''))
  })

  it('renders preview when Preview tab is active', () => {
    render(<CommentComposer onSubmit={vi.fn()} isSubmitting={false} />)
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '## hello' } })
    fireEvent.click(screen.getByText('comments.composer.preview'))
    const heading = screen.getByRole('heading', { level: 2 })
    expect(heading.textContent).toBe('hello')
  })
})
