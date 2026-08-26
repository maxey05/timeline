package com.emgi.timeline.view;

@FunctionalInterface
interface ConfirmPrompt
{
    @FunctionalInterface
    interface Outcome
    {
        void resolved(boolean confirmed);
    }

    void ask(String title, String message, String confirmLabel, String cancelLabel, Outcome outcome);
}