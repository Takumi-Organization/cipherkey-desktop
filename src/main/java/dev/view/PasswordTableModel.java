package dev.view;

import dev.model.PasswordEntry;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Una sola columna: cada celda es la entrada completa y el renderizador pinta la fila rica. */
public class PasswordTableModel extends AbstractTableModel {
    private List<PasswordEntry> entries;

    public PasswordTableModel(List<PasswordEntry> entries) {
        this.entries = entries;
    }

    public void setEntries(List<PasswordEntry> entries) {
        this.entries = entries;
        fireTableDataChanged();
    }

    public PasswordEntry entryAt(int modelRow) {
        return entries.get(modelRow);
    }

    @Override
    public int getRowCount() { return entries.size(); }

    @Override
    public int getColumnCount() { return 1; }

    @Override
    public String getColumnName(int column) { return "Entradas"; }

    @Override
    public Class<?> getColumnClass(int column) { return PasswordEntry.class; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return entries.get(rowIndex);
    }
}
