package com.gentics.mesh.core.verticle.node;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.UPDATE_PERM;
import static com.gentics.mesh.core.rest.common.GenericMessageResponse.message;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

import java.io.File;
import java.util.Optional;
import java.util.Set;

import org.elasticsearch.common.collect.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gentics.mesh.Mesh;
import com.gentics.mesh.core.data.Language;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.node.field.BinaryGraphField;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.data.search.SearchQueueEntryAction;
import com.gentics.mesh.core.image.spi.ImageManipulator;
import com.gentics.mesh.core.rest.common.GenericMessageResponse;
import com.gentics.mesh.core.rest.error.HttpStatusCodeErrorException;
import com.gentics.mesh.core.rest.node.field.BinaryFieldTransformRequest;
import com.gentics.mesh.core.rest.schema.BinaryFieldSchema;
import com.gentics.mesh.core.rest.schema.FieldSchema;
import com.gentics.mesh.core.verticle.handler.AbstractHandler;
import com.gentics.mesh.etc.config.MeshUploadOptions;
import com.gentics.mesh.handler.InternalActionContext;
import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.query.impl.ImageManipulationParameter;
import com.gentics.mesh.util.FileUtils;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.file.FileSystem;
import rx.Observable;

@Component
public class NodeFieldAPIHandler extends AbstractHandler {

	private static final Logger log = LoggerFactory.getLogger(NodeFieldAPIHandler.class);

	@Autowired
	private ImageManipulator imageManipulator;

	public void handleReadField(RoutingContext rc) {
		InternalActionContext ac = InternalActionContext.create(rc);
		db.asyncNoTrxExperimental(() -> {
			Project project = ac.getProject();
			String languageTag = ac.getParameter("languageTag");
			String fieldName = ac.getParameter("fieldName");
			return project.getNodeRoot().loadObject(ac, "uuid", READ_PERM).map(node -> {
				Language language = boot.languageRoot().findByLanguageTag(languageTag);
				if (language == null) {
					throw error(NOT_FOUND, "error_language_not_found", languageTag);
				}

				NodeGraphFieldContainer container = node.getGraphFieldContainer(language);
				if (container == null) {
					throw error(NOT_FOUND, "error_language_not_found", languageTag);
				}

				BinaryGraphField binaryField = container.getBinary(fieldName);
				if (binaryField == null) {
					throw error(NOT_FOUND, "error_binaryfield_not_found_with_name", fieldName);
				}
				return binaryField;
			});
		}).subscribe(binaryField -> {
			db.noTrx(() -> {
				BinaryFieldResponseHandler handler = new BinaryFieldResponseHandler(rc, imageManipulator);
				handler.handle(binaryField);
				return null;
			});
		} , ac::fail);
	}

	public void handleCreateField(RoutingContext rc) {

		InternalActionContext ac = InternalActionContext.create(rc);
		db.asyncNoTrxExperimental(() -> {
			Project project = ac.getProject();
			String languageTag = ac.getParameter("languageTag");
			String fieldName = ac.getParameter("fieldName");
			return project.getNodeRoot().loadObject(ac, "uuid", UPDATE_PERM).map(node -> {
				// TODO Update SQB
				Language language = boot.languageRoot().findByLanguageTag(languageTag);
				if (language == null) {
					throw error(NOT_FOUND, "error_language_not_found", languageTag);
				}
				NodeGraphFieldContainer container = node.getGraphFieldContainer(language);
				if (container == null) {
					throw error(NOT_FOUND, "error_language_not_found", languageTag);
				}

				Optional<FieldSchema> fieldSchema = node.getSchemaContainer().getSchema().getFieldSchema(fieldName);
				if (!fieldSchema.isPresent()) {
					throw error(BAD_REQUEST, "error_schema_definition_not_found", fieldName);
				}
				if (!(fieldSchema.get() instanceof BinaryFieldSchema)) {
					//TODO Add support for other field types
					throw error(BAD_REQUEST, "error_found_field_is_not_binary", fieldName);
				}

				BinaryGraphField field = container.getBinary(fieldName);
				if (field == null) {
					field = container.createBinary(fieldName);
				}
				if (field == null) {
					// ac.fail(BAD_REQUEST, "Binary field {" + fieldName + "} could not be found.");
					// return;
				}

				MeshUploadOptions uploadOptions = Mesh.mesh().getOptions().getUploadOptions();
				try {
					Set<FileUpload> fileUploads = rc.fileUploads();
					if (fileUploads.isEmpty()) {
						throw error(BAD_REQUEST, "node_error_no_binarydata_found");
					}
					if (fileUploads.size() > 1) {
						throw error(BAD_REQUEST, "node_error_more_than_one_binarydata_included");
					}
					FileUpload ul = fileUploads.iterator().next();
					long byteLimit = uploadOptions.getByteLimit();
					if (ul.size() > byteLimit) {
						if (log.isDebugEnabled()) {
							log.debug("Upload size of {" + ul.size() + "} exeeds limit of {" + byteLimit + "} by {" + (ul.size() - byteLimit)
									+ "} bytes.");
						}
						String humanReadableFileSize = org.apache.commons.io.FileUtils.byteCountToDisplaySize(ul.size());
						String humanReadableUploadLimit = org.apache.commons.io.FileUtils.byteCountToDisplaySize(byteLimit);
						throw error(BAD_REQUEST, "node_error_uploadlimit_reached", humanReadableFileSize, humanReadableUploadLimit);
					}
					String contentType = ul.contentType();
					String fileName = ul.fileName();
					String fieldUuid = field.getUuid();

					final BinaryGraphField binaryField = field;
					Observable<String> obsHash = hashAndMoveBinaryFile(ul, fieldUuid, field.getSegmentedPath());
					return obsHash.flatMap(sha512sum -> {
						Tuple<SearchQueueBatch, String> tuple = db.trx(() -> {
							binaryField.setFileName(fileName);
							binaryField.setFileSize(ul.size());
							binaryField.setMimeType(contentType);
							binaryField.setSHA512Sum(sha512sum);
							//TODO handle image properties as well
							// node.setBinaryImageDPI(dpi);
							// node.setBinaryImageHeight(heigth);
							// node.setBinaryImageWidth(width);

							// if the binary field is the segment field, we need to update the webroot info in the node
							if (binaryField.getFieldKey().equals(node.getSchemaContainer().getSchema().getSegmentField())) {
								container.updateWebrootPathInfo("node_conflicting_segmentfield_upload");
							}

							SearchQueueBatch batch = node.addIndexBatch(SearchQueueEntryAction.UPDATE_ACTION);
							return Tuple.tuple(batch, node.getUuid());
						});

						SearchQueueBatch batch = tuple.v1();
						String updatedNodeUuid = tuple.v2();
						return batch.process().map(done -> {
							return message(ac, "node_binary_field_updated", updatedNodeUuid);
						});
					});

				} catch (Exception e) {
					log.error("Could not load schema for node {" + node.getUuid() + "}");
					throw e;
				}
			}).flatMap(x -> x);
		}).subscribe(model -> ac.respond(model, CREATED), ac::fail);

	}

	public void handleUpdateField(RoutingContext rc) {
		handleCreateField(rc);
		//		db.asyncNoTrxExperimental(() -> {
		//			Project project = ac.getProject();
		//			return project.getNodeRoot().loadObject(ac, "uuid", UPDATE_PERM).map(node -> {
		//				// TODO Update SQB
		//				return new GenericMessageResponse("Not yet implemented");
		//			});
		//		}).subscribe(model -> ac.respond(model, OK), ac::fail);
	}

	public void handleRemoveField(InternalActionContext ac) {
		db.asyncNoTrxExperimental(() -> {
			Project project = ac.getProject();
			return project.getNodeRoot().loadObject(ac, "uuid", UPDATE_PERM).map(node -> {
				// TODO Update SQB
				return new GenericMessageResponse("Not yet implemented");
			});
		}).subscribe(model -> ac.respond(model, OK), ac::fail);
	}

	public void handleRemoveFieldItem(InternalActionContext ac) {
		db.asyncNoTrxExperimental(() -> {
			Project project = ac.getProject();
			return project.getNodeRoot().loadObject(ac, "uuid", UPDATE_PERM).map(node -> {
				// TODO Update SQB
				return new GenericMessageResponse("Not yet implemented");
			});
		}).subscribe(model -> ac.respond(model, OK), ac::fail);
	}

	public void handleUpdateFieldItem(InternalActionContext ac) {
		db.asyncNoTrxExperimental(() -> {
			Project project = ac.getProject();
			return project.getNodeRoot().loadObject(ac, "uuid", UPDATE_PERM).map(node -> {
				// TODO Update SQB
				return new GenericMessageResponse("Not yet implemented");
			});
		}).subscribe(model -> ac.respond(model, OK), ac::fail);
	}

	public void handleReadFieldItem(InternalActionContext ac) {
		db.asyncNoTrxExperimental(() -> {
			Project project = ac.getProject();
			return project.getNodeRoot().loadObject(ac, "uuid", READ_PERM).map(node -> {
				return new GenericMessageResponse("Not yet implemented");
			});
		}).subscribe(model -> ac.respond(model, OK), ac::fail);
	}

	public void handleMoveFieldItem(InternalActionContext ac) {
		db.asyncNoTrxExperimental(() -> {
			Project project = ac.getProject();
			return project.getNodeRoot().loadObject(ac, "uuid", UPDATE_PERM).map(node -> {
				// TODO Update SQB
				return new GenericMessageResponse("Not yet implemented");
			});
		}).subscribe(model -> ac.respond(model, OK), ac::fail);
	}

	/**
	 * Handle image transformation
	 * 
	 * @param rc
	 *            routing context
	 */
	public void handleTransformImage(RoutingContext rc) {
		InternalActionContext ac = InternalActionContext.create(rc);
		db.asyncNoTrxExperimental(() -> {
			Project project = ac.getProject();
			String languageTag = ac.getParameter("languageTag");
			String fieldName = ac.getParameter("fieldName");
			return project.getNodeRoot().loadObject(ac, "uuid", UPDATE_PERM).map(node -> {
				// TODO Update SQB
				Language language = boot.languageRoot().findByLanguageTag(languageTag);
				if (language == null) {
					throw error(NOT_FOUND, "error_language_not_found", languageTag);
				}
				NodeGraphFieldContainer container = node.getGraphFieldContainer(language);
				if (container == null) {
					throw error(NOT_FOUND, "error_language_not_found", languageTag);
				}

				Optional<FieldSchema> fieldSchema = node.getSchemaContainer().getSchema().getFieldSchema(fieldName);
				if (!fieldSchema.isPresent()) {
					throw error(BAD_REQUEST, "error_schema_definition_not_found", fieldName);
				}
				if (!(fieldSchema.get() instanceof BinaryFieldSchema)) {
					throw error(BAD_REQUEST, "error_found_field_is_not_binary", fieldName);
				}

				BinaryGraphField field = container.getBinary(fieldName);
				if (field == null) {
					throw error(NOT_FOUND, "error_binaryfield_not_found_with_name", fieldName);
				}

				if (!field.hasImage()) {
					throw error(BAD_REQUEST, "error_transformation_non_image", fieldName);
				}

				try {
					BinaryFieldTransformRequest transformation = JsonUtil.readValue(ac.getBodyAsString(), BinaryFieldTransformRequest.class);
					ImageManipulationParameter imageManipulationParameter = new ImageManipulationParameter().setWidth(transformation.getWidth())
							.setHeight(transformation.getHeight()).setStartx(transformation.getCropx()).setStarty(transformation.getCropy())
							.setCropw(transformation.getCropw()).setCroph(transformation.getCroph());
					if (!imageManipulationParameter.isSet()) {
						throw error(BAD_REQUEST, "error_no_image_transformation", fieldName);
					}
					String fieldUuid = field.getUuid();
					String fieldSegmentedPath = field.getSegmentedPath();

					Observable<Tuple<String, Integer>> obsHashAndSize = imageManipulator
							.handleResize(field.getFile(), field.getSHA512Sum(), imageManipulationParameter).flatMap(buffer -> {
						return hashAndStoreBinaryFile(buffer, fieldUuid, fieldSegmentedPath).map(hash -> {
							return Tuple.tuple(hash, buffer.length());
						});
					});

					return obsHashAndSize.flatMap(hashAndSize -> {
						Tuple<SearchQueueBatch, String> tuple = db.trx(() -> {
							field.setSHA512Sum(hashAndSize.v1());
							field.setFileSize(hashAndSize.v2());
							// resized images will always be jpeg
							field.setMimeType("image/jpeg");

							// TODO should we rename the image, if the extension is wrong?

							//TODO handle image properties as well
							// node.setBinaryImageDPI(dpi);
							// node.setBinaryImageHeight(heigth);
							// node.setBinaryImageWidth(width);
							SearchQueueBatch batch = node.addIndexBatch(SearchQueueEntryAction.UPDATE_ACTION);
							return Tuple.tuple(batch, node.getUuid());
						});

						SearchQueueBatch batch = tuple.v1();
						String updatedNodeUuid = tuple.v2();
						return batch.process().map(done -> {
							return message(ac, "node_binary_field_updated", updatedNodeUuid);
						});
					});
				} catch (HttpStatusCodeErrorException e) {
					throw e;
				} catch (Exception e) {
					log.error("Error while transforming image", e);
					throw error(INTERNAL_SERVER_ERROR, "error_internal");
				}
			}).flatMap(x -> x);
		}).subscribe(model -> ac.respond(model, OK), ac::fail);
	}

	//	// TODO abstract rc away
	//	public void handleDownload(RoutingContext rc) {
	//		InternalActionContext ac = InternalActionContext.create(rc);
	//		BinaryFieldResponseHandler binaryHandler = new BinaryFieldResponseHandler(rc, imageManipulator);
	//		db.asyncNoTrx(() -> {
	//			Project project = ac.getProject();
	//			return project.getNodeRoot().loadObject(ac, "uuid", READ_PERM).map(node-> {
	//				db.noTrx(()-> {
	//					Node node = rh.result();
	//					binaryHandler.handle(node);
	//				});
	//			});
	//		}).subscribe(binaryField -> {
	//		}, ac::fail);
	//	}

	/**
	 * Hash the file upload data and move the temporary uploaded file to its final destination.
	 * 
	 * @param fileUpload
	 *            Upload which will be handled
	 * @param uuid
	 * @param segmentedPath
	 * @return
	 */
	protected Observable<String> hashAndMoveBinaryFile(FileUpload fileUpload, String uuid, String segmentedPath) {
		MeshUploadOptions uploadOptions = Mesh.mesh().getOptions().getUploadOptions();
		File uploadFolder = new File(uploadOptions.getDirectory(), segmentedPath);
		File targetFile = new File(uploadFolder, uuid + ".bin");
		String targetPath = targetFile.getAbsolutePath();

		return hashFileupload(fileUpload).flatMap(sha512sum -> {
			return checkUploadFolderExists(uploadFolder).flatMap(e -> {
				return deletePotentialUpload(targetPath).flatMap(e1 -> {
					return moveUploadIntoPlace(fileUpload, targetPath).map(k -> sha512sum);
				});
			});
		});
	}

	/**
	 * Hash the data from the buffer and store it to its final destination.
	 * 
	 * @param buffer
	 *            buffer which will be handled
	 * @param uuid
	 *            uuid of the binary field
	 * @param segmentedPath
	 *            path to store the binary data
	 * @return observable emitting the sha512 checksum
	 */
	public Observable<String> hashAndStoreBinaryFile(Buffer buffer, String uuid, String segmentedPath) {
		MeshUploadOptions uploadOptions = Mesh.mesh().getOptions().getUploadOptions();
		File uploadFolder = new File(uploadOptions.getDirectory(), segmentedPath);
		File targetFile = new File(uploadFolder, uuid + ".bin");
		String targetPath = targetFile.getAbsolutePath();

		return hashBuffer(buffer).flatMap(sha512sum -> {
			return checkUploadFolderExists(uploadFolder).flatMap(e -> {
				return deletePotentialUpload(targetPath).flatMap(e1 -> {
					return storeBuffer(buffer, targetPath).map(k -> sha512sum);
				});
			});
		});
	}

	/**
	 * Hash the given fileupload and return a sha512 checksum.
	 * 
	 * @param fileUpload
	 * @return
	 */
	protected Observable<String> hashFileupload(FileUpload fileUpload) {
		Observable<String> obsHash = FileUtils.generateSha512Sum(fileUpload.uploadedFileName()).doOnError(error -> {
			log.error("Error while hashing fileupload {" + fileUpload.uploadedFileName() + "}", error);
			throw error(INTERNAL_SERVER_ERROR, "node_error_upload_failed", error);
		});
		return obsHash;
	}

	/**
	 * Hash the given buffer and return a sha512 checksum.
	 * 
	 * @param buffer
	 *            buffer
	 * @return observable emitting the sha512 checksum
	 */
	protected Observable<String> hashBuffer(Buffer buffer) {
		Observable<String> obsHash = FileUtils.generateSha512Sum(buffer).doOnError(error -> {
			log.error("Error while hashing data", error);
			throw error(INTERNAL_SERVER_ERROR, "node_error_upload_failed", error);
		});
		return obsHash;
	}

	/**
	 * Delete potential existing file uploads from the given path.
	 * 
	 * @param targetPath
	 * @return
	 */
	protected Observable<Void> deletePotentialUpload(String targetPath) {
		Vertx rxVertx = Vertx.newInstance(Mesh.vertx());
		FileSystem fileSystem = rxVertx.fileSystem();
		// Deleting of existing binary file
		Observable<Void> obsDeleteExisting = fileSystem.deleteObservable(targetPath).doOnError(error -> {
			log.error("Error while attempting to delete target file {" + targetPath + "}", error);
		});

		Observable<Boolean> obsUploadExistsCheck = fileSystem.existsObservable(targetPath).doOnError(error -> {
			log.error("Unable to check existence of file at location {" + targetPath + "}");
		});

		Observable<Void> obsPotentialUploadDeleted = obsUploadExistsCheck.flatMap(uploadAlreadyExists -> {
			if (uploadAlreadyExists) {
				return obsDeleteExisting.flatMap(e -> {
					return Observable.just(null);
				});
			}
			return Observable.just(null);
		});
		return obsPotentialUploadDeleted;
	}

	/**
	 * Move the fileupload from the temporary upload directory to the given target path.
	 * 
	 * @param fileUpload
	 * @param targetPath
	 * @return
	 */
	protected Observable<Void> moveUploadIntoPlace(FileUpload fileUpload, String targetPath) {
		Vertx rxVertx = Vertx.newInstance(Mesh.vertx());
		FileSystem fileSystem = rxVertx.fileSystem();
		return fileSystem.moveObservable(fileUpload.uploadedFileName(), targetPath).doOnError(error -> {
			log.error("Failed to move upload file from {" + fileUpload.uploadedFileName() + "} to {" + targetPath + "}", error);
			throw error(INTERNAL_SERVER_ERROR, "node_error_upload_failed", error);
		}).flatMap(e -> {
			return Observable.just(null);
		});
	}

	/**
	 * Store the data in the buffer into the given place
	 * 
	 * @param buffer
	 *            buffer
	 * @param targetPath
	 *            target path
	 * @return empty observable
	 */
	protected Observable<Void> storeBuffer(Buffer buffer, String targetPath) {
		Vertx rxVertx = Vertx.newInstance(Mesh.vertx());
		FileSystem fileSystem = rxVertx.fileSystem();
		return fileSystem.writeFileObservable(targetPath, buffer).doOnError(error -> {
			log.error("Failed to save file to {" + targetPath + "}", error);
			throw error(INTERNAL_SERVER_ERROR, "node_error_upload_failed", error);
		});
	}

	/**
	 * Check the target upload folder and create it if needed.
	 * 
	 * @param uploadFolder
	 * @return
	 */
	protected Observable<Void> checkUploadFolderExists(File uploadFolder) {
		Vertx rxVertx = Vertx.newInstance(Mesh.vertx());
		FileSystem fileSystem = rxVertx.fileSystem();
		return fileSystem.existsObservable(uploadFolder.getAbsolutePath()).doOnError(error -> {
			log.error("Could not check whether target directory {" + uploadFolder.getAbsolutePath() + "} exists.", error);
			throw error(BAD_REQUEST, "node_error_upload_failed", error);
		}).flatMap(folderExists -> {
			if (!folderExists) {
				return fileSystem.mkdirsObservable(uploadFolder.getAbsolutePath()).doOnError(error -> {
					log.error("Failed to create target folder {" + uploadFolder.getAbsolutePath() + "}", error);
					throw error(BAD_REQUEST, "node_error_upload_failed", error);
				}).flatMap(e -> {
					return Observable.just(null);
				});
			} else {
				return Observable.just(null);
			}
		});

	}

}
