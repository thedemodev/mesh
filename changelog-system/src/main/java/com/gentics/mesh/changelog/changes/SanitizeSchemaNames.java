package com.gentics.mesh.changelog.changes;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.tinkerpop.gremlin.structure.Direction.OUT;

import java.util.Iterator;

import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.gentics.mesh.changelog.AbstractChange;

import io.vertx.core.json.JsonObject;

public class SanitizeSchemaNames extends AbstractChange {

	@Override
	public String getName() {
		return "Sanitize stored schema and microschema name";
	}

	@Override
	public String getDescription() {
		return "Replaces no longer allowed characters within the schema and microschema name";
	}

	@Override
	public void apply() {
		Vertex meshRoot = getMeshRootVertex();
		Vertex microschemaRoot = meshRoot.getVertices(OUT, "HAS_MICROSCHEMA_ROOT").iterator().next();
		Iterator<Vertex> microschemaIt = microschemaRoot.vertices(OUT, "HAS_SCHEMA_CONTAINER_ITEM");
		while (microschemaIt.hasNext()) {
			Vertex microschemaVertex = microschemaIt.next();
			fixName(microschemaVertex);
			Iterator<Vertex> versionIt = microschemaVertex.vertices(OUT, "HAS_PARENT_CONTAINER");
			while (versionIt.hasNext()) {
				Vertex microschemaVersion = versionIt.next();
				fixName(microschemaVersion);
				String json = microschemaVersion.getProperty("json");
				JsonObject schema = new JsonObject(json);
				String name = schema.getString("name");
				name = name.replaceAll("-", "_");
				schema.put("name", name);
				microschemaVersion.setProperty("json", schema.toString());
			}
		}

		Vertex schemaRoot = meshRoot.vertices(OUT, "HAS_ROOT_SCHEMA").next();
		Iterator<Vertex> schemaIt = schemaRoot.vertices(OUT, "HAS_SCHEMA_CONTAINER_ITEM");
		while (schemaIt.hasNext()) {
			Vertex schemaVertex = schemaIt.next();
			fixName(schemaVertex);
			Iterator<Vertex> versionIt = schemaVertex.vertices(OUT, "HAS_PARENT_CONTAINER");
			while (versionIt.hasNext()) {
				Vertex schemaVersion = versionIt.next();
				fixName(schemaVersion);
				String json = schemaVersion.getProperty("json");
				JsonObject schema = new JsonObject(json);
				String name = schema.getString("name");
				name = name.replaceAll("-", "_");
				schema.put("name", name);
				schemaVersion.setProperty("json", schema.toString());
			}
		}

	}

	private void fixName(Vertex schemaVertex) {
		String name = schemaVertex.getProperty("name");
		if (!isEmpty(name)) {
			name = name.replaceAll("-", "_");
			schemaVertex.setProperty("name", name);
		}
	}

	@Override
	public String getUuid() {
		return "52367EB3E028450BB67EB3E028550B39";
	}

}