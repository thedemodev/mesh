package com.gentics.mesh.graphdb;

import com.syncleus.ferma.FramedTransactionalGraph;

public class Neo4jTrx extends AbstractTrx {

	public Neo4jTrx(FramedTransactionalGraph transaction) {
		init(transaction);
	}
}
