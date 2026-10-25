package com.tuman.browser.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate

/** Dialogs have the Activity lifetime; no Activity reference is retained by the ViewModel. */
class NativePromptDelegate(private val context: Context, private val language: com.tuman.browser.data.Language) : PromptDelegate {
    private var dialog: AlertDialog? = null
    private var cancel: (() -> Unit)? = null
    private var currentPrompt: PromptDelegate.BasePrompt? = null
    fun close() { cancel?.invoke(); cancel = null; dialog?.dismiss(); dialog = null; currentPrompt = null }

    private fun show(prompt: PromptDelegate.BasePrompt, builder: AlertDialog.Builder,
        positive: (() -> PromptDelegate.PromptResponse)? = null): GeckoResult<PromptDelegate.PromptResponse> {
        close()
        val result = GeckoResult<PromptDelegate.PromptResponse>()
        fun dismiss() { if (!prompt.isComplete) result.complete(prompt.dismiss()) }
        builder.setTitle(prompt.title ?: language.text("Website message", "Сообщение сайта"))
            .setNegativeButton(language.text("Cancel", "Отмена")) { _, _ -> dismiss() }
            .setPositiveButton("OK") { _, _ ->
                if (!prompt.isComplete) result.complete(positive?.invoke() ?: prompt.dismiss())
            }.setOnCancelListener { dismiss() }
        cancel = ::dismiss
        currentPrompt = prompt
        prompt.delegate = object : PromptDelegate.PromptInstanceDelegate {
            override fun onPromptDismiss(prompt: PromptDelegate.BasePrompt) {
                if (currentPrompt === prompt) close()
            }
        }
        dialog = builder.create().also { it.show() }
        return result
    }
    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptDelegate.PromptResponse> =
        show(prompt, AlertDialog.Builder(context).setMessage(prompt.message))

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptDelegate.PromptResponse> =
        show(prompt, AlertDialog.Builder(context).setMessage(prompt.message)) { prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE) }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptDelegate.PromptResponse> {
        val field = EditText(context).apply { setText(prompt.defaultValue); maxLines = 4 }
        return show(prompt, AlertDialog.Builder(context).setMessage(prompt.message).setView(field)) { prompt.confirm(field.text.toString()) }
    }
    override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt): GeckoResult<PromptDelegate.PromptResponse> {
        val user = EditText(context).apply { hint = language.text("Username", "Имя пользователя"); setText(prompt.authOptions.username); isSingleLine = true }
        val password = EditText(context).apply { hint = language.text("Password", "Пароль"); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val fields = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 8); addView(user); addView(password) }
        return show(prompt, AlertDialog.Builder(context).setView(fields)) { prompt.confirm(user.text.toString(), password.text.toString()) }
    }
    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptDelegate.PromptResponse> {
        fun flatten(choices: Array<PromptDelegate.ChoicePrompt.Choice>): List<PromptDelegate.ChoicePrompt.Choice> = choices.flatMap {
            it.items?.let(::flatten) ?: if (it.disabled || it.separator) emptyList() else listOf(it)
        }
        val choices = flatten(prompt.choices)
        val selected = choices.filter { it.selected }.map { it.id }.toMutableSet()
        val builder = AlertDialog.Builder(context)
        if (prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
            builder.setMultiChoiceItems(choices.map { it.label }.toTypedArray(), BooleanArray(choices.size) { choices[it].selected }) { _, which, checked ->
                if (checked) selected.add(choices[which].id) else selected.remove(choices[which].id)
            }
        } else builder.setSingleChoiceItems(choices.map { it.label }.toTypedArray(), choices.indexOfFirst { it.selected }) { _, which ->
            selected.clear(); selected.add(choices[which].id)
        }
        return show(prompt, builder) { prompt.confirm(selected.toTypedArray()) }
    }
}
