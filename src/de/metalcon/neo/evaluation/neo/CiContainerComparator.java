/**
 * contains nodes that serve as content items
 * 
 *
 * @author Jonas Kunze, Rene Pickhardt
 * 
 */

package de.metalcon.neo.evaluation.neo;

import java.util.Comparator;

import org.neo4j.graphdb.Node;

public class CiContainerComparator implements Comparator<CiContainer> {
	private final Comparator<Node> nodeComp;

	public CiContainerComparator(Comparator<Node> nodeComp) {
		this.nodeComp = nodeComp;
	}

	public int compare(CiContainer n1, CiContainer n2) {
		return nodeComp.compare(n1.getEntity(), n2.getEntity());
	}
}
