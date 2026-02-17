package org.jetbrains.plugins.template.editor

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class ExploreModelPanel(json: String, onPathSelected: ((String) -> Unit)? = null) : JPanel(BorderLayout()) {
    private val pathField = JTextField()
    private val copyButton = JButton("Copy Path")
    private var selectedPath: String? = null

    init {
        val rootNode = try {
            ObjectMapper().readTree(json)
        } catch (e: OutOfMemoryError) {
            JOptionPane.showMessageDialog(this, "File too large to parse. Out of memory.", "Error", JOptionPane.ERROR_MESSAGE)
            null
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Failed to parse JSON: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
            null
        }
        val treeRoot = if (rootNode != null) createTreeNode("root", rootNode) else DefaultMutableTreeNode("Invalid JSON")
        val tree = JTree(treeRoot)
        val controls = JPanel()
        controls.layout = BoxLayout(controls, BoxLayout.X_AXIS)
        pathField.isEditable = false
        controls.add(JLabel("Path: "))
        controls.add(pathField)
        controls.add(copyButton)
        add(controls, BorderLayout.NORTH)
        add(JScrollPane(tree), BorderLayout.CENTER)

        tree.addTreeSelectionListener(TreeSelectionListener { e ->
            val path = e.path
            selectedPath = computeJsonPath(path)
            pathField.text = selectedPath ?: ""
        })
        copyButton.addActionListener {
            selectedPath?.let {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(it), null)
                JOptionPane.showMessageDialog(this, "Path copied to clipboard!", "Copy", JOptionPane.INFORMATION_MESSAGE)
            }
        }
    }

    private fun createTreeNode(name: String, node: JsonNode): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(name)
        when {
            node.isObject -> node.fields().forEach { (key, value) ->
                treeNode.add(createTreeNode(key, value))
            }
            node.isArray -> node.forEachIndexed { idx, value ->
                treeNode.add(createTreeNode("[$idx]", value))
            }
            else -> treeNode.add(DefaultMutableTreeNode(node.asText()))
        }
        return treeNode
    }

    private fun computeJsonPath(path: TreePath): String {
        val sb = StringBuilder()
        for (i in 1 until path.pathCount) { // skip root
            val node = path.getPathComponent(i).toString()
            if (node.startsWith("[")) sb.append(node) else sb.append(".").append(node)
        }
        return sb.toString().removePrefix(".")
    }
}
