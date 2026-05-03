import { ref } from 'vue'

export function useMouseTooltip() {
  const visible = ref(false)
  const text = ref('')
  const x = ref(0)
  const y = ref(0)

  function show(content: string, event: MouseEvent): void {
    text.value = content
    visible.value = true
    move(event)
  }

  function move(event: MouseEvent): void {
    x.value = event.clientX + 12
    y.value = event.clientY + 12
  }

  function hide(): void {
    visible.value = false
  }

  return { visible, text, x, y, show, move, hide }
}
