package com.gentics.mesh.core.data.node;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Stack;

import com.gentics.mesh.core.data.Language;
import com.gentics.mesh.core.data.MeshAuthUser;
import com.gentics.mesh.core.data.MeshCoreVertex;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.Tag;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.page.impl.PageImpl;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.rest.navigation.NavigationResponse;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.user.NodeReferenceImpl;
import com.gentics.mesh.handler.InternalActionContext;
import com.gentics.mesh.path.Path;
import com.gentics.mesh.path.PathSegment;
import com.gentics.mesh.query.impl.PagingParameter;
import com.gentics.mesh.util.InvalidArgumentException;

import rx.Observable;

/**
 * The Node Domain Model interface.
 * 
 * A node is the main building block for project structures. Each project has one base node which is basically a folder. Additional child nodes can be added to
 * this node and to the created nodes in order to create a project data structure. Each node may be linked to one or more {@link NodeGraphFieldContainer}
 * vertices which contain the language specific data.
 * 
 */
public interface Node extends MeshCoreVertex<NodeResponse, Node> {

	public static final String TYPE = "node";

	/**
	 * Add the given tag to the list of tags for this node.
	 * 
	 * @param tag
	 */
	void addTag(Tag tag);

	/**
	 * Remove the given tag from the list of tags for this node.
	 * 
	 * @param tag
	 */
	void removeTag(Tag tag);

	/**
	 * Return a list of tags that were assigned to this node.
	 * 
	 * @return
	 */
	List<? extends Tag> getTags();

	/**
	 * Return the schema container that holds the schema that is used in combination with this node.
	 * 
	 * @return
	 */
	SchemaContainer getSchemaContainer();

	/**
	 * Set the schema container that is used in combination with this node.
	 * 
	 * @param schema
	 */
	void setSchemaContainer(SchemaContainer schema);

	/**
	 * Return the field container for the given language.
	 * 
	 * @param language
	 * @return
	 */
	NodeGraphFieldContainer getGraphFieldContainer(Language language);

	/**
	 * Return the field container for the given language.
	 * 
	 * @param languageTag
	 * @return
	 */
	NodeGraphFieldContainer getGraphFieldContainer(String languageTag);

	/**
	 * Return the field container for the given language. Create the container when non was found.
	 * 
	 * @param language
	 * @return
	 */
	NodeGraphFieldContainer getOrCreateGraphFieldContainer(Language language);

	/**
	 * Return a list of graph field containers for the node.
	 * 
	 * @return
	 */
	List<? extends NodeGraphFieldContainer> getGraphFieldContainers();

	/**
	 * Return a page of tags that are assigned to the node.
	 * 
	 * @param ac
	 * @return
	 * @throws InvalidArgumentException
	 */
	PageImpl<? extends Tag> getTags(InternalActionContext ac) throws InvalidArgumentException;

	/**
	 * Return a list of language names.
	 * 
	 * @return
	 */
	List<String> getAvailableLanguageNames();

	/**
	 * Return the project of the node.
	 * 
	 * @return
	 */
	Project getProject();

	/**
	 * Set the project of the node.
	 * 
	 * @param project
	 */
	void setProject(Project project);

	/**
	 * Return the list of children for this node.
	 * 
	 * @return
	 */
	List<? extends Node> getChildren();

	/**
	 * Return the list of children for this node, that the given user has read permission for
	 *
	 * @param requestUser user
	 * @return
	 */
	List<? extends Node> getChildren(MeshAuthUser requestUser);

	/**
	 * Returns the parent node of this node.
	 * 
	 * @return
	 */
	Node getParentNode();

	/**
	 * Set the parent node of this node
	 * 
	 * @param parentNode
	 */
	void setParentNode(Node parentNode);

	/**
	 * Create a child node in this node.
	 * 
	 * @param creator
	 * @param schemaContainer
	 * @param project
	 * @return
	 */
	Node create(User creator, SchemaContainer schemaContainer, Project project);

	/**
	 * Return a page with child nodes that are visible to the given user.
	 * 
	 * @param requestUser
	 * @param languageTags
	 * @param pagingParameter
	 * @return
	 * @throws InvalidArgumentException
	 */
	PageImpl<? extends Node> getChildren(MeshAuthUser requestUser, List<String> languageTags, PagingParameter pagingParameter)
			throws InvalidArgumentException;

	/**
	 * Returns the i18n display name for the node. The display name will be determined by loading the i18n field value for the display field parameter of the
	 * node's schema. It may be possible that no display name can be returned since new nodes may not have any values.
	 * 
	 * @param ac
	 * @return
	 */
	String getDisplayName(InternalActionContext ac);

	/**
	 * Find a node field container that matches the nearest possible value for the ?lang= request parameter. When a user requests a node using ?lang=de,en and
	 * there is no de version the en version will be selected and returned.
	 * 
	 * @param languageTags
	 * @return Next matching field container or null when no language matched
	 */
	NodeGraphFieldContainer findNextMatchingFieldContainer(List<String> languageTags);

	/**
	 * Set the published flag.
	 * 
	 * @param published
	 */
	void setPublished(boolean published);

	/**
	 * Return the published flag state.
	 * 
	 * @return
	 */
	boolean isPublished();

	/**
	 * Move this node into the target node.
	 * 
	 * @param ac
	 * @param targetNode
	 * @return
	 */
	Observable<Void> moveTo(InternalActionContext ac, Node targetNode);

	/**
	 * Transform the node into a node reference rest model.
	 * 
	 * @param ac
	 */
	Observable<NodeReferenceImpl> transformToReference(InternalActionContext ac);

	/**
	 * Transform the node into a navigation response rest model.
	 * 
	 * @param ac
	 * @return
	 */
	Observable<NavigationResponse> transformToNavigation(InternalActionContext ac);

	/**
	 * Delete the language container for the given language.
	 * 
	 * @param ac
	 * @param language
	 * @return
	 */
	Observable<? extends Node> deleteLanguageContainer(InternalActionContext ac, Language language);

	/**
	 * Return the path segment of this node.
	 * 
	 * @return
	 */
	Observable<String> getPathSegment(InternalActionContext ac);

	/**
	 * Return the full path to this node.
	 * 
	 * @param ac
	 * @return
	 */
	Observable<String> getPath(InternalActionContext ac);

	/**
	 * Resolve the given path and return the path object that contains the resolved nodes.
	 * 
	 * @param nodePath
	 * @param pathStack
	 * @return
	 */
	Observable<Path> resolvePath(Path nodePath, Stack<String> pathStack);

	/**
	 * Check whether the node provides the given segment for any language or binary attribute filename return the segment information.
	 * 
	 * @param segment
	 * @return Segment information or null if this node is not providing the given segment
	 */
	PathSegment getSegment(String segment);

	/**
	 * Return the webroot path to the node in the given language. If more than one language is given, the path will lead to the first available language of the
	 * node.
	 * 
	 * @param languageTag
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	Observable<String> getPath(String... languageTag) throws UnsupportedEncodingException;

	/**
	 * Return the path segment value of this node in the given language. If more than one language is given, the path will lead to the first available language
	 * of the node.
	 *
	 * @param languageTag
	 * @return
	 */
	Observable<String> getPathSegment(String... languageTag);

	/**
	 * Delete the node and ignore any checks.
	 * 
	 * @param ignoreChecks
	 */
	void delete(boolean ignoreChecks);

	/**
	 * Set the breadcrumb information to the given rest node.
	 * 
	 * @param ac
	 * @param restNode
	 * @return
	 */
	Observable<NodeResponse> setBreadcrumbToRest(InternalActionContext ac, NodeResponse restNode);

}
