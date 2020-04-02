package com.gentics.mesh.core.endpoint.node;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PUBLISHED_PERM;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.context.impl.InternalRoutingActionContextImpl;
import com.gentics.mesh.core.data.Branch;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.node.field.BinaryGraphField;
import com.gentics.mesh.core.endpoint.handler.AbstractHandler;
import com.gentics.mesh.core.verticle.handler.GlobalLock;
import com.gentics.mesh.etc.config.MeshOptions;
import com.gentics.mesh.graphdb.spi.Database;

import io.vertx.ext.web.RoutingContext;

@Singleton
public class BinaryDownloadHandler extends AbstractHandler {

	private final MeshOptions options;
	private final BinaryFieldResponseHandler binaryFieldResponseHandler;
	private final Database db;
	private final GlobalLock globalLock;

	@Inject
	public BinaryDownloadHandler(MeshOptions options, Database db, BinaryFieldResponseHandler binaryFieldResponseHandler, GlobalLock globalLock) {
		this.options = options;
		this.db = db;
		this.binaryFieldResponseHandler = binaryFieldResponseHandler;
		this.globalLock = globalLock;
	}

	public void handleReadBinaryField(RoutingContext rc, String uuid, String fieldName) {
		InternalRoutingActionContextImpl ac = new InternalRoutingActionContextImpl(rc);
		try (GlobalLock lock = globalLock.readLock(ac)) {
			db.tx(() -> {
				Project project = ac.getProject();
				Node node = project.getNodeRoot().loadObjectByUuid(ac, uuid, READ_PUBLISHED_PERM);
				// Language language = boot.get().languageRoot().findByLanguageTag(languageTag);
				// if (language == null) {
				// throw error(NOT_FOUND, "error_language_not_found", languageTag);
				// }

				Branch branch = ac.getBranch(node.getProject());
				NodeGraphFieldContainer fieldContainer = node.findVersion(ac.getNodeParameters().getLanguageList(options), branch.getUuid(),
					ac.getVersioningParameters().getVersion());
				if (fieldContainer == null) {
					throw error(NOT_FOUND, "object_not_found_for_version", ac.getVersioningParameters().getVersion());
				}
				BinaryGraphField field = fieldContainer.getBinary(fieldName);
				if (field == null) {
					throw error(NOT_FOUND, "error_binaryfield_not_found_with_name", fieldName);
				}
				binaryFieldResponseHandler.handle(rc, field);
			});
		}
	}
}
