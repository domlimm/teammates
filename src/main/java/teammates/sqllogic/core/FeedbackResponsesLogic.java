package teammates.sqllogic.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import teammates.common.datatransfer.CourseRoster;
import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.common.datatransfer.questions.FeedbackQuestionType;
import teammates.common.exception.EntityAlreadyExistsException;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.storage.sqlapi.FeedbackResponsesDb;
import teammates.storage.sqlentity.FeedbackQuestion;
import teammates.storage.sqlentity.FeedbackResponse;
import teammates.storage.sqlentity.Student;

/**
 * Handles operations related to feedback sessions.
 *
 * @see FeedbackResponse
 * @see FeedbackResponsesDb
 */
public final class FeedbackResponsesLogic {

    private static final FeedbackResponsesLogic instance = new FeedbackResponsesLogic();

    private FeedbackResponsesDb frDb;

    private FeedbackQuestionsLogic fqLogic;

    private FeedbackResponsesLogic() {
        // prevent initialization
    }

    public static FeedbackResponsesLogic inst() {
        return instance;
    }

    /**
     * Initialize dependencies for {@code FeedbackResponsesLogic}.
     */
    void initLogicDependencies(FeedbackResponsesDb frDb, FeedbackQuestionsLogic fqLogic) {
        this.frDb = frDb;
        this.fqLogic = fqLogic;
    }

    /**
     * Returns true if the responses of the question are visible to students.
     */
    public boolean isResponseOfFeedbackQuestionVisibleToStudent(FeedbackQuestion question) {
        if (question.isResponseVisibleTo(FeedbackParticipantType.STUDENTS)) {
            return true;
        }
        boolean isStudentRecipientType =
                   question.getRecipientType().equals(FeedbackParticipantType.STUDENTS)
                || question.getRecipientType().equals(FeedbackParticipantType.STUDENTS_EXCLUDING_SELF)
                || question.getRecipientType().equals(FeedbackParticipantType.STUDENTS_IN_SAME_SECTION)
                || question.getRecipientType().equals(FeedbackParticipantType.OWN_TEAM_MEMBERS)
                || question.getRecipientType().equals(FeedbackParticipantType.OWN_TEAM_MEMBERS_INCLUDING_SELF)
                || question.getRecipientType().equals(FeedbackParticipantType.GIVER)
                   && question.getGiverType().equals(FeedbackParticipantType.STUDENTS);

        if ((isStudentRecipientType || question.getRecipientType().isTeam())
                && question.isResponseVisibleTo(FeedbackParticipantType.RECEIVER)) {
            return true;
        }
        if (question.getGiverType() == FeedbackParticipantType.TEAMS
                || question.isResponseVisibleTo(FeedbackParticipantType.OWN_TEAM_MEMBERS)) {
            return true;
        }
        return question.isResponseVisibleTo(FeedbackParticipantType.RECEIVER_TEAM_MEMBERS);
    }

    /**
     * Returns true if the responses of the question are visible to instructors.
     */
    public boolean isResponseOfFeedbackQuestionVisibleToInstructor(FeedbackQuestion question) {
        return question.isResponseVisibleTo(FeedbackParticipantType.INSTRUCTORS);
    }

    /**
     * Deletes all feedback responses involved an entity, cascade its associated comments.
     */
    public void deleteFeedbackResponsesInvolvedEntityOfCourseCascade(String courseId, String entityEmail) {
        // delete responses from the entity
        List<FeedbackResponse> responsesFromStudent =
                getFeedbackResponsesFromGiverForCourse(courseId, entityEmail);
        for (FeedbackResponse response : responsesFromStudent) {
            deleteFeedbackResponseCascade(response.getId());
        }

        // delete responses to the entity
        List<FeedbackResponse> responsesToStudent =
                getFeedbackResponsesForReceiverForCourse(courseId, entityEmail);
        for (FeedbackResponse response : responsesToStudent) {
            deleteFeedbackResponseCascade(response.getId());
        }
    }

    /**
     * Gets all responses given by a user for a course.
     */
    public List<FeedbackResponse> getFeedbackResponsesFromGiverForCourse(
            String courseId, String giver) {
        assert courseId != null;
        assert giver != null;

        return frDb.getFeedbackResponsesFromGiverForCourse(courseId, giver);
    }

    /**
     * Gets all responses received by a user for a course.
     */
    public List<FeedbackResponse> getFeedbackResponsesForReceiverForCourse(
            String courseId, String receiver) {
        assert courseId != null;
        assert receiver != null;

        return frDb.getFeedbackResponsesForReceiverForCourse(courseId, receiver);
    }

    /**
     * Deletes a feedback response, cascade its associated comments.
     */
    public void deleteFeedbackResponseCascade(UUID responseId) {
        FeedbackResponse feedbackResponseToDelete = frDb.getFeedbackResponse(responseId);

        frDb.deleteFeedbackResponse(feedbackResponseToDelete);
    }

    /**
     * Updates the relevant responses before the deletion of a student.
     * This method takes care of the following:
     * <ul>
     *     <li>
     *         Making existing responses of 'rank recipient question' consistent.
     *     </li>
     * </ul>
     */
    public void updateFeedbackResponsesForDeletingStudent(String courseId) {
        updateRankRecipientQuestionResponsesAfterDeletingStudent(courseId);
    }

    private void updateRankRecipientQuestionResponsesAfterDeletingStudent(String courseId) {
        List<FeedbackQuestion> filteredQuestions =
                fqLogic.getFeedbackQuestionForCourseWithType(courseId, FeedbackQuestionType.RANK_RECIPIENTS);
        CourseRoster roster = new CourseRoster(
                studentsLogic.getStudentsForCourse(courseId),
                instructorsLogic.getInstructorsForCourse(courseId));
        for (FeedbackQuestion question : filteredQuestions) {
            makeRankRecipientQuestionResponsesConsistent(question, roster);
        }
    }

    // TODO: Below
    /**
     * Makes the rankings by one giver in the response to a 'rank recipient question' consistent, after deleting a
     * student.
     * <p>
     *     Fails silently if the question type is not 'rank recipient question'.
     * </p>
     */
    private void makeRankRecipientQuestionResponsesConsistent(
            FeedbackQuestion question, CourseRoster roster) {
        if (!question.getQuestionType().equals(FeedbackQuestionType.RANK_RECIPIENTS)) {
            return;
        }

        FeedbackParticipantType giverType = question.getGiverType();
        List<FeedbackResponse> responses;

        int numberOfRecipients;
        List<FeedbackResponseAttributes.UpdateOptions> updates = new ArrayList<>();

        switch (giverType) {
        case INSTRUCTORS:
        case SELF:
            for (Instructor instructor : roster.getInstructors()) {
                numberOfRecipients =
                        fqLogic.getRecipientsOfQuestion(question, instructor, null, roster).size();
                responses = getFeedbackResponsesFromGiverForQuestion(question.getId(), instructor.getEmail());
                updates.addAll(FeedbackRankRecipientsResponseDetails
                        .getUpdateOptionsForRankRecipientQuestions(responses, numberOfRecipients));
            }
            break;
        case TEAMS:
        case TEAMS_IN_SAME_SECTION:
            Student firstMemberOfTeam;
            String team;
            Map<String, List<Student>> teams = roster.getTeamToMembersTable();
            for (Map.Entry<String, List<Student>> entry : teams.entrySet()) {
                team = entry.getKey();
                firstMemberOfTeam = entry.getValue().get(0);
                numberOfRecipients =
                        fqLogic.getRecipientsOfQuestion(question, null, firstMemberOfTeam, roster).size();
                responses =
                        getFeedbackResponsesFromTeamForQuestion(question.getId(), question.getCourseId(), team, roster);
                updates.addAll(FeedbackRankRecipientsResponseDetails
                        .getUpdateOptionsForRankRecipientQuestions(responses, numberOfRecipients));
            }
            break;
        default:
            for (Student student : roster.getStudents()) {
                numberOfRecipients =
                        fqLogic.getRecipientsOfQuestion(question, null, student, roster).size();
                responses = getFeedbackResponsesFromGiverForQuestion(question.getId(), student.getEmail());
                updates.addAll(FeedbackRankRecipientsResponseDetails
                        .getUpdateOptionsForRankRecipientQuestions(responses, numberOfRecipients));
            }
            break;
        }

        for (FeedbackResponseAttributes.UpdateOptions update : updates) {
            try {
                frDb.updateFeedbackResponse(update);
            } catch (EntityAlreadyExistsException | EntityDoesNotExistException | InvalidParametersException e) {
                assert false : "Exception occurred when updating responses after deleting students.";
            }
        }
    }

}
