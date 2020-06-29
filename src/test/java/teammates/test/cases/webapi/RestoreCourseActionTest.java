package teammates.test.cases.webapi;

import org.apache.http.HttpStatus;
import org.testng.annotations.Test;

import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.exception.NullHttpParameterException;
import teammates.common.util.Const;
import teammates.storage.api.CoursesDb;
import teammates.ui.webapi.action.JsonResult;
import teammates.ui.webapi.action.RestoreCourseAction;
import teammates.ui.webapi.output.MessageOutput;

/**
 * SUT: {@link RestoreCourseAction}.
 */
public class RestoreCourseActionTest
        extends BaseActionTest<RestoreCourseAction> {

    @Override
    protected String getActionUri() {
        return Const.ResourceURIs.BIN_COURSE;
    }

    @Override
    protected String getRequestMethod() {
        return DELETE;
    }

    @Override
    @Test
    public void testExecute() throws Exception {
        InstructorAttributes instructor1OfCourse1 = typicalBundle.instructors.get("instructor1OfCourse1");
        String instructorId = instructor1OfCourse1.googleId;
        String courseId = instructor1OfCourse1.courseId;

        loginAsInstructor(instructorId);

        ______TS("Not in recycle bin but valid course");

        String[] submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, courseId,
        };

        RestoreCourseAction action = getAction(submissionParams);
        JsonResult result = getJsonResult(action);
        MessageOutput message = (MessageOutput) result.getOutput();

        assertEquals(HttpStatus.SC_OK, result.getStatusCode());
        assertEquals("The course " + courseId + " has been restored.", message.getMessage());
        assertNull(logic.getCourse(instructor1OfCourse1.courseId).deletedAt);

        ______TS("Typical case, restore a deleted course from Recycle Bin");

        submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, courseId,
        };

        logic.moveCourseToRecycleBin(courseId);
        assertEquals(courseId, logic.getSoftDeletedCourseForInstructor(instructor1OfCourse1).getId());

        action = getAction(submissionParams);
        result = getJsonResult(action);
        message = (MessageOutput) result.getOutput();

        assertEquals(HttpStatus.SC_OK, result.getStatusCode());
        assertEquals("The course " + courseId + " has been restored.", message.getMessage());
        assertNull(logic.getCourse(instructor1OfCourse1.courseId).deletedAt);

        ______TS("Not enough parameters");

        NullHttpParameterException exception = assertThrows(NullHttpParameterException.class, () -> {
            RestoreCourseAction emptyParamsAction = getAction();
            getJsonResult(emptyParamsAction);
        });
        assertEquals(String.format(Const.StatusCodes.NULL_HTTP_PARAMETER, Const.ParamsNames.COURSE_ID),
                exception.getMessage());

        ______TS("Non-Existent Course");

        String[] nonExistentCourse = new String[] {
                Const.ParamsNames.COURSE_ID, "123C",
        };
        action = getAction(nonExistentCourse);
        result = getJsonResult(action);
        message = (MessageOutput) result.getOutput();

        assertEquals(HttpStatus.SC_NOT_FOUND, result.getStatusCode());
        assertEquals(CoursesDb.ERROR_UPDATE_NON_EXISTENT, message.getMessage());
    }

    @Override
    @Test
    protected void testAccessControl() throws Exception {
        String[] submissionParams = new String[] {
                Const.ParamsNames.COURSE_ID, "idOfTypicalCourse1",
        };

        verifyOnlyInstructorsOfTheSameCourseWithCorrectCoursePrivilegeCanAccess(
                Const.ParamsNames.INSTRUCTOR_PERMISSION_MODIFY_COURSE, submissionParams);
    }
}
