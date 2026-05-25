import { ref } from 'vue';
import { defineStore } from 'pinia';

export interface DialogDetail {
  label: string;
  current: number;
  required: number;
  completed: boolean;
}

export interface DialogState {
  visible: boolean;
  type: 'confirm' | 'alert';
  title: string;
  message: string;
  details?: DialogDetail[];
  resolve: ((value: boolean) => void) | null;
}

const EMPTY_DIALOG: DialogState = {
  visible: false,
  type: 'confirm',
  title: '',
  message: '',
  details: undefined,
  resolve: null,
};

export const useUiStore = defineStore('ui', () => {
  const rightSummaryOpen = ref(false);
  const dialog = ref<DialogState>({ ...EMPTY_DIALOG });

  function toggleRightSummary() {
    rightSummaryOpen.value = !rightSummaryOpen.value;
  }

  function showConfirm(title: string, message: string, details?: DialogDetail[]): Promise<boolean> {
    return new Promise((resolve) => {
      dialog.value = { visible: true, type: 'confirm', title, message, details, resolve };
    });
  }

  function showAlert(title: string, message: string, details?: DialogDetail[]): Promise<void> {
    return new Promise((resolve) => {
      dialog.value = {
        visible: true,
        type: 'alert',
        title,
        message,
        details,
        resolve: () => resolve(),
      };
    });
  }

  function closeDialog(result: boolean) {
    const cb = dialog.value.resolve;
    dialog.value = { ...EMPTY_DIALOG };
    cb?.(result);
  }

  return { rightSummaryOpen, dialog, toggleRightSummary, showConfirm, showAlert, closeDialog };
});
