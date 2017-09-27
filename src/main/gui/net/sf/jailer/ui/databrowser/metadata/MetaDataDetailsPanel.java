/*
 * Copyright 2007 - 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.jailer.ui.databrowser.metadata;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import net.sf.jailer.ExecutionContext;
import net.sf.jailer.database.Session;
import net.sf.jailer.datamodel.Association;
import net.sf.jailer.datamodel.DataModel;
import net.sf.jailer.datamodel.Table;
import net.sf.jailer.modelbuilder.MetaDataCache.CachedResultSet;
import net.sf.jailer.ui.DbConnectionDialog;
import net.sf.jailer.ui.QueryBuilderDialog;
import net.sf.jailer.ui.QueryBuilderDialog.Relationship;
import net.sf.jailer.ui.databrowser.BrowserContentPane;
import net.sf.jailer.ui.databrowser.BrowserContentPane.LoadJob;
import net.sf.jailer.ui.databrowser.Desktop.RowBrowser;
import net.sf.jailer.ui.databrowser.QueryBuilderPathSelector;
import net.sf.jailer.ui.databrowser.Reference;
import net.sf.jailer.ui.databrowser.Row;
import net.sf.jailer.util.Pair;

/**
 * Meta Data Details View.
 *
 * @author Ralf Wisser
 */
public abstract class MetaDataDetailsPanel extends javax.swing.JPanel {

	private final Reference<DataModel> datamodel;
	private final Session session;
	private final ExecutionContext executionContext;
	private final JFrame owner;
	private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
	private final Map<MetaDataDetails, JPanel> detailsPanels = new HashMap<MetaDataDetails, JPanel>();
	private final Map<Pair<MetaDataDetails, MDTable>, JComponent> detailsViews = new HashMap<Pair<MetaDataDetails, MDTable>, JComponent>();
	private final Map<Table, JComponent> tableDetailsViews = new HashMap<Table, JComponent>();
	
    /**
     * Creates new form MetaDataDetailsPanell 
     */
    public MetaDataDetailsPanel(Reference<DataModel> datamodel, Session session, JFrame owner, ExecutionContext executionContext) {
    	this.datamodel = datamodel;
    	this.session = session;
    	this.owner = owner;
    	this.executionContext = executionContext;
        initComponents();
        
        for (MetaDataDetails mdd: MetaDataDetails.values()) {
        	JPanel panel = new JPanel(new BorderLayout());
        	detailsPanels.put(mdd, panel);
        	tabbedPane.addTab(mdd.name, panel);
        }
        
        Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				for (;;) {
					try {
						queue.take().run();
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}
			}
		});
        thread.setDaemon(true);
        thread.start();
    }

	public void clear() {
    	setVisible(false);
	}

    public void showMetaDataDetails(final MDTable mdTable, Table table, DataModel dataModel) {
    	setVisible(true);
    	tableDetailsPanel.removeAll();
    	if (table != null) {
    		JComponent view = tableDetailsViews.get(table);
    		if (view == null) {
    			view = new TableDetailsView(table, mdTable, this, dataModel);
    			tableDetailsViews.put(table, view);
    		}
    		tableDetailsPanel.add(view);
    	} else {
    		JButton analyseButton = new JButton("Analyse schema \"" + mdTable.getSchema().getName() + "\"");
    		analyseButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					analyseSchema(mdTable.getSchema().getName());
				}
			});
    		JPanel panel = new JPanel(new GridBagLayout());
    		GridBagConstraints gridBagConstraints = new java.awt.GridBagConstraints();
	        gridBagConstraints.gridx = 1;
	        gridBagConstraints.gridy = 1;
	        gridBagConstraints.gridwidth = 1;
	        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
	        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    		panel.add(new JLabel("  Table \"" + mdTable.getName() + "\""), gridBagConstraints);
    		gridBagConstraints = new java.awt.GridBagConstraints();
	        gridBagConstraints.gridx = 1;
	        gridBagConstraints.gridy = 2;
	        gridBagConstraints.gridwidth = 1;
	        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
	        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    		panel.add(new JLabel("  is not part of the data model."), gridBagConstraints);
    		gridBagConstraints = new java.awt.GridBagConstraints();
	        gridBagConstraints.gridx = 1;
	        gridBagConstraints.gridy = 3;
	        gridBagConstraints.gridwidth = 1;
	        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
	        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    		panel.add(analyseButton, gridBagConstraints);
    		tableDetailsPanel.add(panel);
    	}
		tabbedPane.repaint();
    	queue.clear();
    	for (final MetaDataDetails mdd: MetaDataDetails.values()) {
	    	final JPanel panel = detailsPanels.get(mdd);
	    	panel.removeAll();
	    	final Pair<MetaDataDetails, MDTable> cacheKey = new Pair<MetaDataDetails, MDTable>(mdd, mdTable);
	    	if (detailsViews.containsKey(cacheKey)) {
	    		JComponent comp = detailsViews.get(cacheKey);
				panel.add(comp);
				tabbedPane.repaint();
				continue;
	    	}
	    	panel.add(new JLabel(" loading..."));
	    	tabbedPane.repaint();
	    	try {
		    	final int tableNameColumnIndex = 3;
		    	final Set<String> pkNames = new HashSet<String>();
				try {
					pkNames.addAll(mdTable.getPrimaryKeyColumns());
				} catch (SQLException e1) {
				}
		    	final BrowserContentPane rb = new BrowserContentPane(datamodel.get(), null, "", session, null, null,
						null, null, new HashSet<Pair<BrowserContentPane, Row>>(), new HashSet<Pair<BrowserContentPane, String>>(), 0, false, false, executionContext) {
		    		{
		    			noSingleRowDetailsView = true;
		    		}
		    		@Override
					protected void unhide() {
					}
					@Override
					protected void showInNewWindow() {
					}
					@Override
					protected void reloadDataModel() throws Exception {
					}
					@Override
					protected void openSchemaMappingDialog() {
					}
					@Override
					protected void openSchemaAnalyzer() {
					}
					@Override
					protected void onRedraw() {
						tabbedPane.repaint();
					}
					@Override
					protected void onHide() {
					}
					@Override
					protected void onContentChange(List<Row> rows, boolean reloadChildren) {
					}
					@Override
					protected void navigateTo(Association association, int rowIndex, Row row) {
					}
					@Override
					protected List<RowBrowser> getTableBrowser() {
						return null;
					}
					@Override
					protected PriorityBlockingQueue<RunnableWithPriority> getRunnableQueue() {
						return null;
					}
					@Override
					protected QueryBuilderPathSelector getQueryBuilderPathSelector() {
						return null;
					}
					@Override
					protected QueryBuilderDialog getQueryBuilderDialog() {
						return null;
					}
					@Override
					protected RowBrowser getParentBrowser() {
						return null;
					}
					@Override
					protected JFrame getOwner() {
						return owner;
					}
					@Override
					protected double getLayoutFactor() {
						return 0;
					}
					@Override
					protected DbConnectionDialog getDbConnectionDialog() {
						return null;
					}
					@Override
					protected List<RowBrowser> getChildBrowsers() {
						return new ArrayList<RowBrowser>();
					}
					@Override
					protected void findClosure(Row row, Set<Pair<BrowserContentPane, Row>> closure, boolean forward) {
						Pair<BrowserContentPane, Row> thisRow = new Pair<BrowserContentPane, Row>(this, row);
						if (!closure.contains(thisRow)) {
							closure.add(thisRow);
						}
					}
					@Override
					protected void findClosure(Row row) {
						Set<Pair<BrowserContentPane, Row>> rows = new HashSet<Pair<BrowserContentPane, Row>>();
						findClosure(row, rows, false);
						currentClosure.addAll(rows);
						rows = new HashSet<Pair<BrowserContentPane, Row>>();
						findClosure(row, rows, true);
						currentClosure.addAll(rows);
					}
					@Override
					protected Relationship createQBRelations(boolean withParents) {
						return null;
					}
					@Override
					protected List<Relationship> createQBChildrenRelations(RowBrowser tabu, boolean all) {
						return null;
					}
					@Override
					protected void collectPositions(Map<String, Map<String, double[]>> positions) {
					}
					@Override
					protected void close() {
					}
					@Override
					protected void beforeReload() {
					}
					@Override
					protected void appendLayout() {
					}
					@Override
					protected void adjustClosure(BrowserContentPane tabu) {
					}
					@Override
					protected void addRowToRowLink(Row pRow, Row exRow) {
					}
					@Override
					protected boolean renderRowAsPK(Row theRow) {
						if (tableNameColumnIndex >= 0 && tableNameColumnIndex < theRow.values.length) {
							return pkNames.contains(theRow.values[tableNameColumnIndex]);
						}
						return false;
					}
					@Override
					protected MetaDataSource getMetaDataSource() {
						return null;
					}
				};
		    	
				final CachedResultSet[] metaDataDetails = new CachedResultSet[1];
				
				queue.put(new Runnable() {
					@Override
					public void run() {
				    	try {
				    		ResultSet rs = mdd.readMetaDataDetails(session, mdTable);
				    		metaDataDetails[0] = new CachedResultSet(rs);
				    		rs.close();
						} catch (SQLException e) {
							e.printStackTrace();
						}
			    		SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								LoadJob loadJob = rb.newLoadJob(metaDataDetails[0]);
					    		loadJob.run();
					        	JComponent rTabContainer = rb.getRowsTableContainer();
						    	detailsViews.put(cacheKey, rTabContainer);
								final JTable rTab = rb.getRowsTable();
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {
										mdd.adjustRowsTable(rTab);
										panel.removeAll();
							        	JComponent rTabContainer = rb.getRowsTableContainer();
										panel.add(rTabContainer);
							        	tabbedPane.repaint();
									}
								});
							}
						});
					}
				});
			} catch (InterruptedException e) {
				// ignore
			}
    	}
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        tabbedPane = new javax.swing.JTabbedPane();
        tableDetailsPanel = new javax.swing.JPanel();

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.LINE_AXIS));

        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.LINE_AXIS));

        tableDetailsPanel.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab("Table", tableDetailsPanel);

        jPanel1.add(tabbedPane);

        add(jPanel1);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel jPanel1;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JPanel tableDetailsPanel;
    // End of variables declaration//GEN-END:variables

    protected abstract void analyseSchema(String schemaName);

}
