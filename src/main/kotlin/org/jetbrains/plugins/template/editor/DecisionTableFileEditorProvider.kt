package org.jetbrains.plugins.template.editor

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBPanel
import com.intellij.ui.table.JBTable
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.openapi.fileEditor.FileEditorPolicy
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.table.AbstractTableModel
import com.intellij.ui.components.JBScrollPane
import java.beans.PropertyChangeListener
import com.fasterxml.jackson.databind.JsonNode
import javax.swing.JFileChooser
import org.jetbrains.annotations.NotNull

// Data classes for the decision table JSON structure
// (You can expand these as needed to match your JSON schema)
@JsonIgnoreProperties(ignoreUnknown = true)
data class DecisionTable(
    val version: String = "",
    @param:JsonProperty("match_policy")
    val matchPolicy: String = "",
    @param:JsonProperty("no_match_policy")
    val noMatchPolicy: String = "",
    val cols: MutableList<DTColumn> = mutableListOf(),
    val rows: MutableList<DTRow> = mutableListOf()
)
data class DTColumn(val name: String = "", val type: String = "", @param:JsonProperty("data_type") val dataType: String = "")
data class DTRow(var cols: MutableList<DTCell> = mutableListOf())
data class DTCell(val name: String = "", val value: String = "")

object DecisionTableJsonUtil {
    private val mapper = jacksonObjectMapper()
    fun loadFromFile(file: java.io.File): DecisionTable {
        val root = mapper.readTree(file) ?: return DecisionTable()
        val tableNode = root.get("decision_table") ?: return DecisionTable()
        return mapper.treeToValue(tableNode, DecisionTable::class.java) ?: DecisionTable()
    }
    fun saveToFile(table: DecisionTable, file: java.io.File) {
        val root = mapper.createObjectNode()
        val node: JsonNode = mapper.valueToTree(table)
        root.set<JsonNode>("decision_table", node)
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, root)
    }
}

class DecisionTableFileEditorProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.extension == "json"
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return DecisionTableFileEditor(file)
    }

    override fun getEditorTypeId(): String = "decision-table-json-editor"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
}

class DecisionTableFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val statusLabel = javax.swing.JLabel("")
    private var tableModel: DecisionTableTableModel
    private val panel: JPanel

    init {
        val path = file.path
        val decisionTable = DecisionTableJsonUtil.loadFromFile(java.io.File(path))
        tableModel = DecisionTableTableModel(decisionTable)
        panel = createEditorPanel(tableModel, path)
    }

    private fun createEditorPanel(model: DecisionTableTableModel, filePath: String): JPanel {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = java.awt.BorderLayout()
        val table = JBTable(model)
        table.autoResizeMode = JBTable.AUTO_RESIZE_OFF
        table.fillsViewportHeight = true
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION)
        table.setCellSelectionEnabled(true)
        table.setRowSelectionAllowed(true)
        table.setColumnSelectionAllowed(true)
        table.setShowGrid(true)
        table.setGridColor(com.intellij.ui.JBColor.LIGHT_GRAY)
        table.setAutoCreateRowSorter(true)
        table.rowHeight = 24
        table.font = java.awt.Font("Dialog", java.awt.Font.PLAIN, 14)
        val scrollPane = JBScrollPane(table)
        panel.add(scrollPane, java.awt.BorderLayout.CENTER)

        // Button panel
        val buttonPanel = JPanel()
        buttonPanel.layout = java.awt.FlowLayout(java.awt.FlowLayout.LEFT)
        val saveButton = JButton("Save")
        val addRowButton = JButton("Add Row")
        val addColButton = JButton("Add Column")
        val deleteRowButton = JButton("Delete Row")
        val deleteColButton = JButton("Delete Column")
        val exportCsvButton = JButton("Export CSV")
        saveButton.toolTipText = "Save changes to file"
        addRowButton.toolTipText = "Add a new row"
        addColButton.toolTipText = "Add a new column"
        deleteRowButton.toolTipText = "Delete selected row"
        deleteColButton.toolTipText = "Delete selected column"
        exportCsvButton.toolTipText = "Export table as CSV"
        // Optionally set icons if available
        // saveButton.icon = ...
        // addRowButton.icon = ...
        // addColButton.icon = ...
        // deleteRowButton.icon = ...
        // deleteColButton.icon = ...
        // exportCsvButton.icon = ...
        buttonPanel.add(saveButton)
        buttonPanel.add(addRowButton)
        buttonPanel.add(addColButton)
        buttonPanel.add(deleteRowButton)
        buttonPanel.add(deleteColButton)
        buttonPanel.add(exportCsvButton)
        panel.add(buttonPanel, java.awt.BorderLayout.NORTH)
        panel.add(statusLabel, java.awt.BorderLayout.SOUTH)

        saveButton.addActionListener {
            try {
                DecisionTableJsonUtil.saveToFile(model.toDecisionTable(), java.io.File(filePath))
                file.refresh(false, false)
                statusLabel.text = "Saved successfully!"
                JOptionPane.showMessageDialog(panel, "Saved successfully!", "Save", JOptionPane.INFORMATION_MESSAGE)
            } catch (_: Exception) {
                statusLabel.text = "Failed to save."
                JOptionPane.showMessageDialog(panel, "Failed to save.", "Error", JOptionPane.ERROR_MESSAGE)
            }
        }
        addRowButton.addActionListener {
            model.addRow()
            statusLabel.text = "Row added."
        }
        addColButton.addActionListener {
            val colName = JOptionPane.showInputDialog(panel, "Enter new column name:")
            if (!colName.isNullOrBlank()) {
                model.addColumn(colName.trim())
                statusLabel.text = "Column '$colName' added."
            }
        }
        deleteRowButton.addActionListener {
            val selectedRow = table.selectedRow
            if (selectedRow >= 0) {
                model.removeRow(selectedRow)
                statusLabel.text = "Row deleted."
            } else {
                statusLabel.text = "No row selected."
                JOptionPane.showMessageDialog(panel, "No row selected.", "Delete Row", JOptionPane.WARNING_MESSAGE)
            }
        }
        deleteColButton.addActionListener {
            val selectedCol = table.selectedColumn
            if (selectedCol >= 0) {
                model.removeColumn(selectedCol)
                statusLabel.text = "Column deleted."
            } else {
                statusLabel.text = "No column selected."
                JOptionPane.showMessageDialog(panel, "No column selected.", "Delete Column", JOptionPane.WARNING_MESSAGE)
            }
        }
        exportCsvButton.addActionListener {
            try {
                val chooser = JFileChooser()
                chooser.dialogTitle = "Export as CSV"
                chooser.selectedFile = java.io.File(file.nameWithoutExtension + ".csv")
                val result = chooser.showSaveDialog(panel)
                if (result == JFileChooser.APPROVE_OPTION) {
                    val csvFile = chooser.selectedFile
                    val csvContent = model.toCsv()
                    csvFile.writeText(csvContent)
                    statusLabel.text = "Exported to ${'$'}{csvFile.absolutePath}"
                    JOptionPane.showMessageDialog(panel, "Exported to ${'$'}{csvFile.absolutePath}", "Export CSV", JOptionPane.INFORMATION_MESSAGE)
                }
            } catch (_: Exception) {
                statusLabel.text = "Failed to export."
                JOptionPane.showMessageDialog(panel, "Failed to export.", "Error", JOptionPane.ERROR_MESSAGE)
            }
        }
        // Keyboard shortcuts
        saveButton.mnemonic = 'S'.code
        addRowButton.mnemonic = 'R'.code
        addColButton.mnemonic = 'C'.code
        deleteRowButton.mnemonic = 'D'.code
        deleteColButton.mnemonic = 'L'.code
        exportCsvButton.mnemonic = 'E'.code
        return panel
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun getName(): String = "Decision Table Editor"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        // No-op
    }
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        // No-op
    }
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun dispose() {}
    @NotNull
    override fun getFile(): VirtualFile = file
}

class DecisionTableTableModel(
    private val decisionTable: DecisionTable
) : AbstractTableModel() {
    override fun getRowCount(): Int = decisionTable.rows.size
    override fun getColumnCount(): Int = decisionTable.cols.size
    override fun getColumnName(col: Int): String = decisionTable.cols.getOrNull(col)?.name ?: ""
    override fun getValueAt(row: Int, col: Int): Any {
        val rowObj = decisionTable.rows.getOrNull(row) ?: return ""
        val colName = decisionTable.cols.getOrNull(col)?.name ?: return ""
        return rowObj.cols.find { it.name == colName }?.value ?: ""
    }
    override fun isCellEditable(row: Int, col: Int): Boolean = true
    override fun setValueAt(aValue: Any?, row: Int, col: Int) {
        val rowObj = decisionTable.rows.getOrNull(row) ?: return
        val colName = decisionTable.cols.getOrNull(col)?.name ?: return
        val cellIdx = rowObj.cols.indexOfFirst { it.name == colName }
        if (cellIdx >= 0) {
            rowObj.cols[cellIdx] = rowObj.cols[cellIdx].copy(value = aValue?.toString() ?: "")
        } else {
            rowObj.cols.add(DTCell(name = colName, value = aValue?.toString() ?: ""))
        }
        fireTableCellUpdated(row, col)
    }
    fun addRow() {
        val newRow = DTRow(cols = decisionTable.cols.map { DTCell(it.name, "") }.toMutableList())
        decisionTable.rows.add(newRow)
        fireTableRowsInserted(decisionTable.rows.size - 1, decisionTable.rows.size - 1)
    }
    fun addColumn(colName: String) {
        val newCol = DTColumn(name = colName, type = "EVALUATE", dataType = "STRING")
        decisionTable.cols.add(newCol)
        // Add empty cell for new column in all rows
        decisionTable.rows.forEach { row ->
            row.cols.add(DTCell(name = colName, value = ""))
        }
        fireTableStructureChanged()
    }
    fun removeRow(rowIndex: Int) {
        if (rowIndex in 0 until decisionTable.rows.size) {
            decisionTable.rows.removeAt(rowIndex)
            fireTableRowsDeleted(rowIndex, rowIndex)
        }
    }
    fun removeColumn(colIndex: Int) {
        if (colIndex in 0 until decisionTable.cols.size) {
            val colName = decisionTable.cols[colIndex].name
            decisionTable.cols.removeAt(colIndex)
            decisionTable.rows.forEach { row ->
                row.cols.removeIf { it.name == colName }
            }
            fireTableStructureChanged()
        }
    }
    fun toDecisionTable(): DecisionTable = decisionTable

    fun toCsv(): String {
        val header = decisionTable.cols.joinToString(",") { it.name }
        val rows = decisionTable.rows.joinToString("\n") { row ->
            row.cols.joinToString(",") { cell -> cell.value }
        }
        return "$header\n$rows"
    }
}

class UnifyFileViewerProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.extension == "json"
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return UnifyFileViewerEditor(file)
    }

    override fun getEditorTypeId(): String = "unify-file-viewer"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
}

class UnifyFileViewerEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val panel: JPanel
    private val editorView: DecisionTableFileEditor
    private val explorerView: ExploreModelFileEditor
    private var currentView: JComponent

    init {
        editorView = DecisionTableFileEditor(file)
        explorerView = ExploreModelFileEditor(file)
        panel = JPanel(java.awt.BorderLayout())
        val switchPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT))
        val editorButton = JButton("Decision Table Editor")
        val explorerButton = JButton("Explore Model")
        switchPanel.add(editorButton)
        switchPanel.add(explorerButton)
        panel.add(switchPanel, java.awt.BorderLayout.NORTH)
        // Use ExploreModelPanel with path highlight callback
        val explorePanel = ExploreModelPanel(file.inputStream.bufferedReader().use { it.readText() }) { jsonPath ->
            // Switch to editor view and highlight path
            panel.remove(currentView)
            currentView = editorView.component
            panel.add(currentView, java.awt.BorderLayout.CENTER)
            panel.revalidate()
            panel.repaint()
            // TODO: Implement highlight logic in editorView for jsonPath
        }
        currentView = explorePanel
        panel.add(currentView, java.awt.BorderLayout.CENTER)
        editorButton.addActionListener {
            panel.remove(currentView)
            currentView = editorView.component
            panel.add(currentView, java.awt.BorderLayout.CENTER)
            panel.revalidate()
            panel.repaint()
        }
        explorerButton.addActionListener {
            panel.remove(currentView)
            currentView = explorePanel
            panel.add(currentView, java.awt.BorderLayout.CENTER)
            panel.revalidate()
            panel.repaint()
        }
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun getName(): String = "Unify File Viewer"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() {}
    override fun getFile(): VirtualFile = file
}
