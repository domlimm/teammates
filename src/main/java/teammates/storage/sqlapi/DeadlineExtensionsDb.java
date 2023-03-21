package teammates.storage.sqlapi;

import static teammates.common.util.Const.ERROR_CREATE_ENTITY_ALREADY_EXISTS;
import static teammates.common.util.Const.ERROR_UPDATE_NON_EXISTENT;

import java.util.List;
import java.util.UUID;

import teammates.common.exception.EntityAlreadyExistsException;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.HibernateUtil;
import teammates.storage.sqlentity.Course;
import teammates.storage.sqlentity.DeadlineExtension;
import teammates.storage.sqlentity.FeedbackSession;
import teammates.storage.sqlentity.User;

import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;

/**
 * Handles CRUD operations for deadline extensions.
 *
 * @see DeadlineExtension
 */
public final class DeadlineExtensionsDb extends EntitiesDb {

    private static final DeadlineExtensionsDb instance = new DeadlineExtensionsDb();

    private DeadlineExtensionsDb() {
        // prevent initialization
    }

    public static DeadlineExtensionsDb inst() {
        return instance;
    }

    /**
     * Creates a deadline extension.
     */
    public DeadlineExtension createDeadlineExtension(DeadlineExtension de)
            throws InvalidParametersException, EntityAlreadyExistsException {
        assert de != null;

        if (!de.isValid()) {
            throw new InvalidParametersException(de.getInvalidityInfo());
        }

        if (getDeadlineExtension(de.getId()) != null) {
            throw new EntityAlreadyExistsException(
                    String.format(ERROR_CREATE_ENTITY_ALREADY_EXISTS, de.toString()));
        }

        persist(de);
        return de;
    }

    /**
     * Gets a deadline extension by {@code id}.
     */
    public DeadlineExtension getDeadlineExtension(UUID id) {
        assert id != null;

        return HibernateUtil.get(DeadlineExtension.class, id);
    }

    /**
     * Get DeadlineExtension by {@code userId} and {@code feedbackSessionId}.
     */
    public DeadlineExtension getDeadlineExtension(UUID userId, UUID feedbackSessionId) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<DeadlineExtension> cr = cb.createQuery(DeadlineExtension.class);
        Root<DeadlineExtension> root = cr.from(DeadlineExtension.class);

        cr.select(root).where(cb.and(
                cb.equal(root.get("sessionId"), feedbackSessionId),
                cb.equal(root.get("userId"), userId)));

        TypedQuery<DeadlineExtension> query = HibernateUtil.createQuery(cr);
        return query.getResultStream().findFirst().orElse(null);
    }

    /**
     * Saves an updated {@code DeadlineExtension} to the db.
     *
     * @return updated deadline extension
     * @throws InvalidParametersException  if attributes to update are not valid
     * @throws EntityDoesNotExistException if the deadline extension cannot be found
     */
    public DeadlineExtension updateDeadlineExtension(DeadlineExtension deadlineExtension)
            throws InvalidParametersException, EntityDoesNotExistException {
        assert deadlineExtension != null;

        if (!deadlineExtension.isValid()) {
            throw new InvalidParametersException(deadlineExtension.getInvalidityInfo());
        }

        if (getDeadlineExtension(deadlineExtension.getId()) == null) {
            throw new EntityDoesNotExistException(ERROR_UPDATE_NON_EXISTENT);
        }

        return merge(deadlineExtension);
    }

    /**
     * Deletes a deadline extension.
     */
    public void deleteDeadlineExtension(DeadlineExtension de) {
        if (de != null) {
            delete(de);
        }
    }

    /**
     * Gets the DeadlineExtension with the specified {@code feedbackSessionId} and {@code userId} if it exists.
     * Otherwise, return null.
     */
    public DeadlineExtension getDeadlineExtensionForUser(UUID feedbackSessionId, UUID userId) {
        assert feedbackSessionId != null;
        assert userId != null;

        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<DeadlineExtension> cr = cb.createQuery(DeadlineExtension.class);
        Root<DeadlineExtension> deadlineExtensionRoot = cr.from(DeadlineExtension.class);
        Join<DeadlineExtension, User> userJoin = deadlineExtensionRoot.join("user");
        Join<DeadlineExtension, FeedbackSession> sessionJoin = deadlineExtensionRoot.join("feedbackSession");

        cr.select(deadlineExtensionRoot).where(cb.and(
                cb.equal(sessionJoin.get("id"), feedbackSessionId),
                cb.equal(userJoin.get("id"), userId)));

        return HibernateUtil.createQuery(cr).getResultStream().findFirst().orElse(null);
    }

    /**
     * Deletes deadline extensions associated with {@code courseId} and {@code userEmail}.
     */
    public void deleteDeadlineExtensions(String courseId, String userEmail, boolean isInstructor) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<DeadlineExtension> cr = cb.createQuery(DeadlineExtension.class);
        Root<DeadlineExtension> deadlineExtensionRoot = cr.from(DeadlineExtension.class);
        Join<DeadlineExtension, User> usersJoin = deadlineExtensionRoot.join("user");
        Join<User, Course> coursesJoin = usersJoin.join("course");

        cr.select(deadlineExtensionRoot)
                .where(cb.and(
                    cb.equal(coursesJoin.get("id"), courseId),
                    cb.equal(usersJoin.get("email"), userEmail)));

        List<DeadlineExtension> deadlineExtensionsToDelete = HibernateUtil.createQuery(cr).getResultList();

        for (DeadlineExtension deadlineExtension : deadlineExtensionsToDelete) {
            deleteDeadlineExtension(deadlineExtension);
        }
    }
}
