package com.gentics.mesh.core.field.binary;

import static com.gentics.mesh.core.data.relationship.GraphPermission.UPDATE_PERM;
import static com.gentics.mesh.demo.TestDataProvider.PROJECT_NAME;
import static com.gentics.mesh.util.MeshAssert.assertSuccess;
import static com.gentics.mesh.util.MeshAssert.latchFor;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.mesh.Mesh;
import com.gentics.mesh.core.AbstractSpringVerticle;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.node.field.BinaryGraphField;
import com.gentics.mesh.core.node.AbstractBinaryVerticleTest;
import com.gentics.mesh.core.rest.common.GenericMessageResponse;
import com.gentics.mesh.core.rest.node.NodeDownloadResponse;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.field.BinaryField;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.core.rest.schema.impl.StringFieldSchemaImpl;
import com.gentics.mesh.core.verticle.node.NodeVerticle;
import com.gentics.mesh.util.UUIDUtil;

import io.vertx.core.Future;

public class BinaryGraphNodeVerticleTest extends AbstractBinaryVerticleTest {

	@Autowired
	private NodeVerticle nodeVerticle;

	@Override
	public List<AbstractSpringVerticle> getAdditionalVertices() {
		List<AbstractSpringVerticle> list = new ArrayList<>();
		list.add(nodeVerticle);
		return list;
	}

	@Test
	public void testUploadWithNoPerm() throws IOException {
		String contentType = "application/octet-stream";
		int binaryLen = 8000;
		String fileName = "somefile.dat";
		Node node = folder("news");
		prepareSchema(node, "", "binary");
		role().revokePermissions(node, UPDATE_PERM);

		Future<GenericMessageResponse> future = updateBinaryField(node, "en", "binary", binaryLen, contentType, fileName);
		latchFor(future);
		expectException(future, FORBIDDEN, "error_missing_perm", node.getUuid());
	}

	@Test
	@Ignore("mimetype whitelist is not yet implemented")
	public void testUploadWithInvalidMimetype() throws IOException {

		String contentType = "application/octet-stream";
		int binaryLen = 10000;
		String fileName = "somefile.dat";
		Node node = folder("news");
		String whitelistRegex = "image/.*";
		prepareSchema(node, whitelistRegex, "binary");

		Future<GenericMessageResponse> future = updateBinaryField(node, "en", "binary", binaryLen, contentType, fileName);
		latchFor(future);
		expectException(future, BAD_REQUEST, "node_error_invalid_mimetype", contentType, whitelistRegex);
	}

	@Test
	public void testUploadMultipleToBinaryNode() throws IOException {
		String contentType = "application/octet-stream";
		int binaryLen = 10000;

		Node node = folder("news");
		prepareSchema(node, "", "binary");

		for (int i = 0; i < 20; i++) {
			NodeGraphFieldContainer container = node.getGraphFieldContainer("en");
			BinaryGraphField oldValue = container.getBinary("binary");
			String fileName = "somefile" + i + ".dat";

			call(() -> updateBinaryField(node, "en", "binary", binaryLen, contentType, fileName));
			node.reload();
			container.reload();

			assertEquals("The binary filename was not updated.", fileName,
					node.getGraphFieldContainer(english()).getBinary("binary").getFileName());

			NodeResponse response = readNode(PROJECT_NAME, node.getUuid());
			assertEquals("Check version number", container.getVersion().nextDraft().toString(), response.getVersion().getNumber());
			assertThat(container.getBinary("binary")).isEqualToComparingFieldByField(oldValue);
		}
	}

	@Test
	public void testUploadToNonBinaryField() throws IOException {
		String contentType = "application/octet-stream";
		int binaryLen = 10000;
		String fileName = "somefile.dat";

		Node node = folder("news");

		// Add a schema called nonBinary
		Schema schema = node.getSchemaContainer().getLatestVersion().getSchema();
		schema.addField(new StringFieldSchemaImpl().setName("nonBinary").setLabel("No Binary content"));
		node.getSchemaContainer().getLatestVersion().setSchema(schema);

		Future<GenericMessageResponse> future = updateBinaryField(node, "en", "nonBinary", binaryLen, contentType, fileName);
		latchFor(future);
		expectException(future, BAD_REQUEST, "error_found_field_is_not_binary", "nonBinary");
	}

	@Test
	public void testUploadToNodeWithoutBinaryField() throws IOException {
		String contentType = "application/octet-stream";
		int binaryLen = 10000;
		String fileName = "somefile.dat";
		Node node = folder("news");

		Future<GenericMessageResponse> future = updateBinaryField(node, "en", "nonBinary", binaryLen, contentType, fileName);
		latchFor(future);
		expectException(future, BAD_REQUEST, "error_schema_definition_not_found", "nonBinary");
	}

	/**
	 * Test whether the implementation works as expected when you update the node binary data to an image and back to a non image. The image related fields
	 * should disappear.
	 */
	@Test
	@Ignore("image prop handling not yet implemented")
	public void testUpdateBinaryToImageAndNonImage() {

	}

	@Test
	public void testFileUploadLimit() throws IOException {

		int binaryLen = 10000;
		Mesh.mesh().getOptions().getUploadOptions().setByteLimit(binaryLen - 1);
		String contentType = "application/octet-stream";
		String fileName = "somefile.dat";

		Node node = folder("news");
		prepareSchema(node, "", "binary");

		Future<GenericMessageResponse> future = updateBinaryField(node, "en", "binary", binaryLen, contentType, fileName);
		latchFor(future);
		expectException(future, BAD_REQUEST, "node_error_uploadlimit_reached", "9 KB", "9 KB");
	}

	@Test
	public void testPathSegmentation() throws IOException {
		Node node = folder("news");
		node.setUuid(UUIDUtil.randomUUID());

		// Add some test data
		prepareSchema(node, "", "binary");
		String contentType = "application/octet-stream";
		String fileName = "somefile.dat";
		int binaryLen = 10000;
		Future<GenericMessageResponse> future = updateBinaryField(node, "en", "binary", binaryLen, contentType, fileName);
		latchFor(future);
		assertSuccess(future);

		// Load the uploaded binary field and return the segment path to the field
		BinaryGraphField binaryField = node.getGraphFieldContainer(english())
				.getBinary("binary");
		String uuid = "b677504736ed47a1b7504736ed07a14a";
		binaryField.setUuid(uuid);
		String path = binaryField.getSegmentedPath();
		assertEquals("/b677/5047/36ed/47a1/b750/4736/ed07/a14a/", path);
	}

	@Test
	public void testUpload() throws Exception {

		String contentType = "application/octet-stream";
		int binaryLen = 8000;
		String fileName = "somefile.dat";
		Node node = folder("news");
		prepareSchema(node, "", "binary");

		Future<GenericMessageResponse> future = updateBinaryField(node, "en", "binary", binaryLen, contentType, fileName);
		latchFor(future);
		assertSuccess(future);
		expectResponseMessage(future, "node_binary_field_updated", node.getUuid());

		node.reload();

		Future<NodeResponse> responseFuture = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid());
		latchFor(responseFuture);
		assertSuccess(responseFuture);
		NodeResponse response = responseFuture.result();

		BinaryField binaryField = response.getFields().getBinaryField("binary");
		assertEquals("The filename should be set in the response.", fileName, binaryField.getFileName());
		assertEquals("The contentType was correctly set in the response.", contentType, binaryField.getMimeType());
		assertEquals("The binary length was not correctly set in the response.", binaryLen, binaryField.getFileSize());
		assertNotNull("The hashsum was not found in the response.", binaryField.getSha512sum());
		assertNull("The data did not contain image information.", binaryField.getDpi());
		assertNull("The data did not contain image information.", binaryField.getWidth());
		assertNull("The data did not contain image information.", binaryField.getHeight());

		Future<NodeDownloadResponse> downloadFuture = getClient().downloadBinaryField(PROJECT_NAME, node.getUuid(), "en", "binary");
		latchFor(downloadFuture);
		assertSuccess(downloadFuture);
		NodeDownloadResponse downloadResponse = downloadFuture.result();
		assertNotNull(downloadResponse);
		assertNotNull(downloadResponse.getBuffer().getByte(1));
		assertNotNull(downloadResponse.getBuffer().getByte(binaryLen));
		assertEquals(binaryLen, downloadResponse.getBuffer().length());
		assertEquals(contentType, downloadResponse.getContentType());
		assertEquals(fileName, downloadResponse.getFilename());
	}

	@Test
	public void testUploadWithConflict() throws IOException {
		String contentType = "application/octet-stream";
		int binaryLen = 10;
		String fileName = "somefile.dat";
		Node folder2014 = folder("2014");
		Node folder2015 = folder("2015");
		prepareSchema(folder2014, "", "binary");

		// make binary field the segment field
		Schema schema = folder2014.getSchemaContainer().getLatestVersion().getSchema();
		schema.setSegmentField("binary");
		folder2014.getSchemaContainer().getLatestVersion().setSchema(schema);

		// upload file to folder 2014
		Future<GenericMessageResponse> uploadFuture = updateBinaryField(folder2014, "en", "binary", binaryLen, contentType, fileName);
		latchFor(uploadFuture);
		assertSuccess(uploadFuture);

		// try to upload same file to folder 2015
		uploadFuture = updateBinaryField(folder2015, "en", "binary", binaryLen, contentType, fileName);
		latchFor(uploadFuture);
		expectException(uploadFuture, CONFLICT, "node_conflicting_segmentfield_upload", "binary", fileName);
	}

	@Ignore("Image properties are not yet parsed")
	@Test
	public void testUploadImage() throws IOException {
		String contentType = "image/png";
		String fieldName = "image";
		int binaryLen = 8000;
		String fileName = "somefile.png";
		Node node = folder("news");
		prepareSchema(node, "", fieldName);

		Future<GenericMessageResponse> future = updateBinaryField(node, "en", fieldName, binaryLen, contentType, fileName);
		latchFor(future);
		assertSuccess(future);
		expectResponseMessage(future, "node_binary_field_updated", node.getUuid());

		node.reload();

		Future<NodeResponse> responseFuture = getClient().findNodeByUuid(PROJECT_NAME, node.getUuid());
		latchFor(responseFuture);
		assertSuccess(responseFuture);
		NodeResponse response = responseFuture.result();

		BinaryField binaryField = response.getFields().getBinaryField(fieldName);
		assertEquals("The filename should be set in the response.", fileName, binaryField.getFileName());
		assertEquals("The contentType was correctly set in the response.", contentType, binaryField.getMimeType());
		assertEquals("The binary length was not correctly set in the response.", binaryLen, binaryField.getFileSize());
		assertNotNull("The hashsum was not found in the response.", binaryField.getSha512sum());
		assertNotNull("The data did not contain image information.", binaryField.getDpi());
		assertNotNull("The data did not contain image information.", binaryField.getWidth());
		assertNotNull("The data did not contain image information.", binaryField.getHeight());

		Future<NodeDownloadResponse> downloadFuture = getClient().downloadBinaryField(PROJECT_NAME, node.getUuid(), "en", fieldName);
		latchFor(downloadFuture);
		assertSuccess(downloadFuture);
		NodeDownloadResponse downloadResponse = downloadFuture.result();
		assertNotNull(downloadResponse);
		assertNotNull(downloadResponse.getBuffer().getByte(1));
		assertNotNull(downloadResponse.getBuffer().getByte(binaryLen));
		assertEquals(binaryLen, downloadResponse.getBuffer().length());
		assertEquals(contentType, downloadResponse.getContentType());
		assertEquals(fileName, downloadResponse.getFilename());
	}

}