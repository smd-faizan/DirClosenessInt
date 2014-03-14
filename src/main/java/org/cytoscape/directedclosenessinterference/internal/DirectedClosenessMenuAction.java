/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cytoscape.directedclosenessinterference.internal;

import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.swing.AbstractCyAction;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.model.*;
import org.cytoscape.view.model.CyNetworkView;

/**
 *
 * @author faizi
 */
public class DirectedClosenessMenuAction extends AbstractCyAction {

    public CyApplicationManager cyApplicationManager;
    public CySwingApplication cyDesktopService;
    public CyActivator cyactivator;

    public DirectedClosenessMenuAction(CyApplicationManager cyApplicationManager, final String menuTitle, CyActivator cyactivator) {
        super(menuTitle, cyApplicationManager, null, null);
        setPreferredMenu("Apps");
        this.cyactivator = cyactivator;
        this.cyApplicationManager = cyApplicationManager;
        this.cyDesktopService = cyactivator.getcytoscapeDesktopService();
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        //Get the selected nodes
        List<CyNode> selectedNodes = CyTableUtil.getNodesInState(cyApplicationManager.getCurrentNetwork(), "selected", true);
        if (selectedNodes.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Please Select atleast 1 node");
            return;
        }

        Thread timer = new Thread() {

            @Override
            public void run() {
                //Get the selected nodes
                List<CyNode> selectedNodes = CyTableUtil.getNodesInState(cyApplicationManager.getCurrentNetwork(), "selected", true);

                StringBuffer buffer = new StringBuffer();
                CyApplicationManager manager = cyApplicationManager;
                CyNetworkView networkView = manager.getCurrentNetworkView();
                networkView.updateView();
                CyNetwork network = networkView.getModel();

                List<CyNode> nodeList = network.getNodeList();
                int n = nodeList.size();
                CyTable edgeTable = network.getDefaultEdgeTable();
                double[][] adjM = createAdjMatrix(network, nodeList, edgeTable, n);
                double[][] D = adjM.clone();
                runFloydAlgo(D);

                Map<CyNode, Double> closenessValuesOfM = new HashMap<CyNode, Double>();
                int i = 0;
                for (CyNode root : nodeList) {
                    Double sum = 0.0;
                    for (int k = 0; k < n; k++) {
                        if (D[i][k] != 31999.0) {
                            sum = sum + D[i][k];
                        }
                    }
                    i++;
                    closenessValuesOfM.put(root, 1 / sum);
                }

                //make the new network
                int n2 = n - selectedNodes.size();
                double[][] M2 = new double[n2][n2];
                i = 0;
                int i2 = 0;
                for (CyNode root : nodeList) {
                    if (selectedNodes.contains(root)) {
                        // do not add
                        i2++;
                        continue;
                    }
                    int j = 0;
                    int j2 = 0;
                    for (CyNode root2 : nodeList) {
                        if (selectedNodes.contains(root2)) {
                            // do not add
                            j2++;
                            continue;
                        }
                        M2[i][j] = adjM[i - i2][j - j2];
                        // rest all by default zeroes in the adjacency matrix
                        j++;
                    }
                    i++;
                }
                double[][] D2 = M2.clone();
                runFloydAlgo(D2);

                Map<CyNode, Double> closenessValuesOfM2 = new HashMap<CyNode, Double>();
                i = 0;
                for (CyNode root : nodeList) {
                    if (selectedNodes.contains(root)) {
                        continue;
                    }
                    Double sum = 0.0;
                    int k = 0;
                    for (CyNode root2 : nodeList) {
                        if (selectedNodes.contains(root2)) {
                            continue;
                        }
                        if (D[i][k] != 31999.0) {
                            sum = sum + D[i][k];
                        }
                        k++;
                    }
                    i++;
                    closenessValuesOfM2.put(root, 1 / sum);
                }
                
                Map<CyNode, Double> interferenceValues = calculateInterference(closenessValuesOfM, closenessValuesOfM2);
                
                
                buffer.append("Directed Closeness centralities of whole network (with all nodes): ");
                buffer.append("\n");
                int k = 0;
                for (CyNode root : nodeList) {
                    if (k < n) {
                        buffer.append(network.getRow(root).get("name",
                                String.class));
                        buffer.append(": ");
                        buffer.append(closenessValuesOfM.get(root));
                        buffer.append("\n");
                        k++;
                    }
                }

                buffer.append("Directed Closeness centralities of network without the selected nodes: ");
                buffer.append("\n");
                k = 0;
                for (CyNode root : nodeList) {
                    if (k < n2) {
                        if (!(selectedNodes.contains(root))) {
                            buffer.append(network.getRow(root).get("name",
                                    String.class));
                            buffer.append(": ");
                            buffer.append(closenessValuesOfM2.get(root));
                            buffer.append("\n");
                            k++;
                        }
                    }
                }
                buffer.append("\n");
                buffer.append("Directed Closeness interference in % : ");
                buffer.append("\n");
                for (Map.Entry entry : interferenceValues.entrySet()) {
                    buffer.append(network.getRow((CyNode) entry.getKey()).get("name",
                            String.class));
                    buffer.append(": ");
                    buffer.append((Double) entry.getValue());
                    buffer.append("\n");
                }

                System.out.println(buffer);
                JOptionPane.showMessageDialog(null, buffer);


            }
        };
        timer.start();
    }

    public static double[][] createAdjMatrix(CyNetwork currentnetwork, List<CyNode> nodeList, CyTable edgeTable, int totalnodecount) {
        //make an adjacencymatrix for the current network
        double[][] adjacencyMatrixOfNetwork = new double[totalnodecount][totalnodecount];
        for (int i = 0; i < totalnodecount; i++) {
            for (int j = 0; j < totalnodecount; j++) {
                adjacencyMatrixOfNetwork[i][j] = 31999.0;
            }
        }
        int k = 0;
        for (CyNode root : nodeList) {
            List<CyNode> neighbors = currentnetwork.getNeighborList(root, CyEdge.Type.ANY);
            for (CyNode neighbor : neighbors) {
                adjacencyMatrixOfNetwork[k][nodeList.indexOf(neighbor)] = 1.0;
                if (currentnetwork.getRow(root).get("Layer", Integer.class) == currentnetwork.getRow(neighbor).get("Layer", Integer.class)) {
                    List<CyEdge> edges = currentnetwork.getConnectingEdgeList(root, neighbor, CyEdge.Type.DIRECTED);
                    if (edges.size() > 0) {
                        CyRow row = edgeTable.getRow(edges.get(0).getSUID());
                        try {
                            adjacencyMatrixOfNetwork[k][nodeList.indexOf(neighbor)] = Double.parseDouble(row.get(CyEdge.INTERACTION, String.class));
                        } catch (NumberFormatException ex) {
                        }
                    }
                }
            }
            k++;
        }
        return adjacencyMatrixOfNetwork;
    }

    public static int[][] runFloydAlgo(double[][] D) {
        int totalnodecount = D.length;

        int[][] Pi = new int[totalnodecount][totalnodecount];
        for (int i = 0; i < totalnodecount; i++) {
            for (int j = 0; j < totalnodecount; j++) {
                if (i == j || D[i][j] == 31999.0) {
                    Pi[i][j] = -1;
                } else {
                    Pi[i][j] = i;
                }
            }
        }
        for (int k = 0; k < totalnodecount; k++) {
            for (int i = 0; i < totalnodecount; i++) {
                for (int j = 0; j < totalnodecount; j++) {
                    if (i != j) {
                        if (D[i][j] > D[i][k] + D[k][j]) {
                            D[i][j] = D[i][k] + D[k][j];
                            Pi[i][j] = Pi[k][j];
                        }
                    }
                }
            }
        }
        return Pi;
    }

    public static Map<CyNode, Double> calculateInterference(Map<CyNode, Double> valuesOfM1, Map<CyNode, Double> valuesOfM2) {
        Double totalSum1 = 0.0;
        Double totalSum2 = 0.0;
        for (Map.Entry entry : valuesOfM1.entrySet()) {
            totalSum1 = totalSum1 + (Double) entry.getValue();
        }
        for (Map.Entry entry : valuesOfM2.entrySet()) {
            totalSum2 = totalSum2 + (Double) entry.getValue();
        }
        Map<CyNode, Double> interferenceValues = new HashMap<CyNode, Double>();

        for (Map.Entry entry : valuesOfM2.entrySet()) {
            CyNode currentNode = (CyNode) entry.getKey();
            Double interferenceValue = ((Double) valuesOfM1.get(currentNode)) / totalSum1;
            interferenceValue = interferenceValue - ((Double) entry.getValue()) / totalSum2;
            interferenceValues.put(currentNode, (interferenceValue) * 100);
        }
        return interferenceValues;
    }

    public static void printMatrix(double[][] matrix, String name) {
        System.out.println("The matrix " + name + " is :");
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix.length; j++) {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println();
        }
    }
}
