package teammates.storage.sqlapi;

import static teammates.common.util.Const.ERROR_CREATE_ENTITY_ALREADY_EXISTS;
import static teammates.common.util.Const.ERROR_UPDATE_NON_EXISTENT;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import teammates.common.exception.EntityAlreadyExistsException;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.HibernateUtil;
import teammates.storage.sqlentity.Course;
import teammates.storage.sqlentity.FeedbackQuestion;
import teammates.storage.sqlentity.FeedbackResponse;
import teammates.storage.sqlentity.FeedbackSession;
import teammates.storage.sqlentity.responses.FeedbackRankRecipientsResponse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;

/**
 * Handles CRUD operations for feedbackResponses.
 *
 * @see FeedbackResponse
 */
public final class FeedbackResponsesDb extends EntitiesDb {

    private static final FeedbackResponsesDb instance = new FeedbackResponsesDb();

    private FeedbackResponsesDb() {
        // prevent initialization
    }

    public static FeedbackResponsesDb inst() {
        return instance;
    }

    /**
     * Gets a feedbackResponse or null if it does not exist.
     */
    public FeedbackResponse getFeedbackResponse(UUID frId) {
        assert frId != null;

        return HibernateUtil.get(FeedbackResponse.class, frId);
    }

    /**
     * Gets all responses given by a user in a course.
     */
    public List<FeedbackResponse> getFeedbackResponsesFromGiverForCourse(
            String courseId, String giver) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<FeedbackResponse> cr = cb.createQuery(FeedbackResponse.class);
        Root<FeedbackResponse> frRoot = cr.from(FeedbackResponse.class);
        Join<FeedbackResponse, FeedbackQuestion> fqJoin = frRoot.join("feedbackQuestion");
        Join<FeedbackQuestion, FeedbackSession> fsJoin = fqJoin.join("feedbackSession");

        cr.select(frRoot)
                .where(cb.and(
                    cb.equal(fsJoin.get("courseId"), courseId),
                    cb.equal(frRoot.get("giver"), giver)));

        return HibernateUtil.createQuery(cr).getResultList();
    }

    /**
     * Gets all responses given to a user in a course.
     */
    public List<FeedbackResponse> getFeedbackResponsesForReceiverForCourse(String courseId, String receiver) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<FeedbackResponse> cr = cb.createQuery(FeedbackResponse.class);
        Root<FeedbackResponse> frRoot = cr.from(FeedbackResponse.class);
        Join<FeedbackResponse, FeedbackQuestion> fqJoin = frRoot.join("feedbackQuestion");
        Join<FeedbackQuestion, FeedbackSession> fsJoin = fqJoin.join("feedbackSession");

        cr.select(frRoot)
                .where(cb.and(
                    cb.equal(fsJoin.get("courseId"), courseId),
                    cb.equal(frRoot.get("receiver"), receiver)));

        return HibernateUtil.createQuery(cr).getResultList();
    }

    /**
     * Gets all responses given by a user for a question.
     */
    public List<FeedbackResponse> getFeedbackResponsesFromGiverForQuestion(
            UUID feedbackQuestionId, String giver) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<FeedbackResponse> cr = cb.createQuery(FeedbackResponse.class);
        Root<FeedbackResponse> frRoot = cr.from(FeedbackResponse.class);
        Join<FeedbackResponse, FeedbackQuestion> fqJoin = frRoot.join("feedbackQuestion");

        cr.select(frRoot)
                .where(cb.and(
                        cb.equal(fqJoin.get("id"), feedbackQuestionId),
                        cb.equal(frRoot.get("giver"), giver)));

        return HibernateUtil.createQuery(cr).getResultList();
    }

    /**
     * Creates a feedbackResponse.
     */
    public FeedbackResponse createFeedbackResponse(FeedbackResponse feedbackResponse)
            throws InvalidParametersException, EntityAlreadyExistsException {
        assert feedbackResponse != null;

        if (!feedbackResponse.isValid()) {
            throw new InvalidParametersException(feedbackResponse.getInvalidityInfo());
        }

        if (getFeedbackResponse(feedbackResponse.getId()) != null) {
            throw new EntityAlreadyExistsException(
                    String.format(ERROR_CREATE_ENTITY_ALREADY_EXISTS, feedbackResponse.toString()));
        }

        persist(feedbackResponse);
        return feedbackResponse;
    }

    /**
     * Updates a feedback response.
     *
     * <p>If the giver/recipient field is changed, the response is updated by recreating the response
     * as question-giver-recipient is the primary key.
     *
     * @return updated feedback response
     * @throws InvalidParametersException if attributes to update are not valid
     * @throws EntityDoesNotExistException if the comment cannot be found
     * @throws EntityAlreadyExistsException if the response cannot be updated
     *         by recreation because of an existent response
     */
    public FeedbackResponse updateFeedbackResponse(FeedbackResponse feedbackResponse)
            throws EntityDoesNotExistException, InvalidParametersException, EntityAlreadyExistsException {
        assert feedbackResponse != null;

        if (!feedbackResponse.isValid()) {
            throw new InvalidParametersException(feedbackResponse.getInvalidityInfo());
        }

        FeedbackResponse oldResponse = getFeedbackResponse(feedbackResponse.getId());
        if (oldResponse == null) {
            throw new EntityDoesNotExistException(ERROR_UPDATE_NON_EXISTENT);
        }

        if (feedbackResponse.getReceiver().equals(oldResponse.getReceiver())
                && feedbackResponse.getGiver().equals(oldResponse.getGiver())) {
            // update only if change
            boolean hasSameAttributes =
                    oldResponse.getGiverSection().getName().equals(feedbackResponse.getGiverSection().getName())
                    && oldResponse.getReceiverSection().getName()
                            .equals(feedbackResponse.getReceiverSection().getName())
                    && ((FeedbackRankRecipientsResponse) oldResponse).getAnswer()
                            .equals(((FeedbackRankRecipientsResponse) feedbackResponse).getAnswer());

            if (hasSameAttributes) {
                return feedbackResponse;
            }

            oldResponse.setGiverSection(feedbackResponse.getGiverSection());
            oldResponse.setReceiverSection(feedbackResponse.getReceiverSection());
            ((FeedbackRankRecipientsResponse) oldResponse)
                    .setAnswer(((FeedbackRankRecipientsResponse) feedbackResponse).getAnswer());

            merge(oldResponse);

            return oldResponse;            
        } else {
            // need to recreate the entity
            createFeedbackResponse(feedbackResponse);
            delete(oldResponse);

            return feedbackResponse;
        }
    }

    /**
     * Deletes a feedbackResponse.
     */
    public void deleteFeedbackResponse(FeedbackResponse feedbackResponse) {
        if (feedbackResponse != null) {
            delete(feedbackResponse);
        }
    }

    /**
     * Gets the feedback responses for a feedback question.
     * @param feedbackQuestionId the Id of the feedback question.
     * @param giverEmail the email of the response giver.
     */
    public List<FeedbackResponse> getFeedbackResponsesFromGiverForQuestion(
            UUID feedbackQuestionId, String giverEmail) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<FeedbackResponse> cq = cb.createQuery(FeedbackResponse.class);
        Root<FeedbackResponse> root = cq.from(FeedbackResponse.class);
        Join<FeedbackResponse, FeedbackQuestion> frJoin = root.join("feedbackQuestion");
        cq.select(root)
                .where(cb.and(
                        cb.equal(frJoin.get("id"), feedbackQuestionId),
                        cb.equal(root.get("giver"), giverEmail)));
        return HibernateUtil.createQuery(cq).getResultList();
    }

    /**
     * Deletes all feedback responses of a question cascade its associated comments.
     */
    public void deleteFeedbackResponsesForQuestionCascade(UUID feedbackQuestionId) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<FeedbackResponse> cq = cb.createQuery(FeedbackResponse.class);
        Root<FeedbackResponse> frRoot = cq.from(FeedbackResponse.class);
        Join<FeedbackResponse, FeedbackQuestion> fqJoin = frRoot.join("feedbackQuestion");
        cq.select(frRoot).where(cb.equal(fqJoin.get("id"), feedbackQuestionId));
        List<FeedbackResponse> frToBeDeleted = HibernateUtil.createQuery(cq).getResultList();
        frToBeDeleted.forEach(HibernateUtil::remove);
    }

    /**
     * Checks whether there are responses for a question.
     */
    public boolean areThereResponsesForQuestion(UUID questionId) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<FeedbackResponse> cq = cb.createQuery(FeedbackResponse.class);
        Root<FeedbackResponse> root = cq.from(FeedbackResponse.class);
        Join<FeedbackResponse, FeedbackQuestion> fqJoin = root.join("feedbackQuestion");

        cq.select(root)
                .where(cb.equal(fqJoin.get("id"), questionId));
        return !HibernateUtil.createQuery(cq).getResultList().isEmpty();
    }

    /**
     * Checks whether a user has responses in a session.
     */
    public boolean hasResponsesFromGiverInSession(
            String giver, String feedbackSessionName, String courseId) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<FeedbackResponse> cq = cb.createQuery(FeedbackResponse.class);
        Root<FeedbackResponse> root = cq.from(FeedbackResponse.class);
        Join<FeedbackResponse, FeedbackQuestion> fqJoin = root.join("feedbackQuestion");
        Join<FeedbackQuestion, FeedbackSession> fsJoin = fqJoin.join("feedbackSession");
        Join<FeedbackSession, Course> courseJoin = fsJoin.join("course");

        cq.select(root)
                .where(cb.and(
                        cb.equal(root.get("giver"), giver),
                        cb.equal(fsJoin.get("name"), feedbackSessionName),
                        cb.equal(courseJoin.get("id"), courseId)));

        return !HibernateUtil.createQuery(cq).getResultList().isEmpty();
    }

    /**
     * Checks whether there are responses for a course.
     */
    public boolean hasResponsesForCourse(String courseId) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<FeedbackResponse> cq = cb.createQuery(FeedbackResponse.class);
        Root<FeedbackResponse> root = cq.from(FeedbackResponse.class);
        Join<FeedbackResponse, FeedbackQuestion> fqJoin = root.join("feedbackQuestion");
        Join<FeedbackQuestion, FeedbackSession> fsJoin = fqJoin.join("feedbackSession");
        Join<FeedbackSession, Course> courseJoin = fsJoin.join("course");

        cq.select(root)
                .where(cb.equal(courseJoin.get("id"), courseId));

        return !HibernateUtil.createQuery(cq).getResultList().isEmpty();
    }
}
