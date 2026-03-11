package com.github.blueboxgarage.unifyfileviewer.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

class ExploreModelFileEditorProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        // Accept all files with .json extension (not directories)
        return !file.isDirectory && file.extension == "json"
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return ExploreModelFileEditor(file)
    }

    override fun getEditorTypeId(): String = "explore-model-json-viewer"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
}
