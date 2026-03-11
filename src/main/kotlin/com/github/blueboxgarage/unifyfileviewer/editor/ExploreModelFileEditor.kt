package com.github.blueboxgarage.unifyfileviewer.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent
import java.beans.PropertyChangeListener
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.github.blueboxgarage.unifyfileviewer.editor.ExploreModelPanel

class ExploreModelFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val panel: ExploreModelPanel

    init {
        val content = file.inputStream.bufferedReader().use { it.readText() }
        panel = ExploreModelPanel(content)
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun getName(): String = "Explore Model"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() {}
    override fun getFile(): VirtualFile = file
}
