package com.gentics.mesh.core.schema.field;

/**
 * Interface for tests for field migrations
 */
public interface FieldMigrationTest {
	/**
	 * Test removing the field
	 * @throws Exception
	 */
	void testRemove() throws Exception;

	/**
	 * Test renaming the field
	 * @throws Exception
	 */
	void testRename() throws Exception;

	/**
	 * Test changing the field to a binary field
	 * @throws Exception
	 */
	void testChangeToBinary() throws Exception;

	/**
	 * Test changing the field to a boolean field
	 * @throws Exception
	 */
	void testChangeToBoolean() throws Exception;

	/**
	 * Test changing to a boolean list field
	 * @throws Exception
	 */
	void testChangeToBooleanList() throws Exception;

	/**
	 * Test changing to date field
	 * @throws Exception
	 */
	void testChangeToDate() throws Exception;

	/**
	 * Test changing to date list field
	 * @throws Exception
	 */
	void testChangeToDateList() throws Exception;

	/**
	 * Test changing to html field
	 * @throws Exception
	 */
	void testChangeToHtml() throws Exception;

	/**
	 * Test changing to html list field
	 * @throws Exception
	 */
	void testChangeToHtmlList() throws Exception;

	/**
	 * Test changing to micronode field
	 * @throws Exception
	 */
	void testChangeToMicronode() throws Exception;

	/**
	 * Test changing to micronode list field
	 * @throws Exception
	 */
	void testChangeToMicronodeList() throws Exception;

	/**
	 * Test changing to node field
	 * @throws Exception
	 */
	void testChangeToNode() throws Exception;

	/**
	 * Test changing to node list field
	 * @throws Exception
	 */
	void testChangeToNodeList() throws Exception;

	/**
	 * Test changing to number field
	 * @throws Exception
	 */
	void testChangeToNumber() throws Exception;

	/**
	 * Test changing to number list field
	 * @throws Exception
	 */
	void testChangeToNumberList() throws Exception;

	/**
	 * Test changing to string field
	 * @throws Exception
	 */
	void testChangeToString() throws Exception;

	/**
	 * Test changing to string list field
	 * @throws Exception
	 */
	void testChangeToStringList() throws Exception;

	/**
	 * Test migrating the field with a custom migration script
	 * @throws Exception
	 */
	void testCustomMigrationScript() throws Exception;

	/**
	 * Test with an invalid migration script
	 * @throws Exception
	 */
	void testInvalidMigrationScript() throws Exception;

	/**
	 * Test that trying System.exit in a migration script will throw an error but not kill the JVM
	 * @throws Exception
	 */
	void testSystemExit() throws Exception;
}
