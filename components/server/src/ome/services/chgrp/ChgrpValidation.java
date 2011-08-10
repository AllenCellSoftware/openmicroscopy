/*
 *   $Id$
 *
 *   Copyright 2011 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.chgrp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ome.model.IObject;
import ome.services.graphs.GraphEntry;
import ome.services.graphs.GraphException;
import ome.services.graphs.GraphOpts;
import ome.services.graphs.GraphSpec;
import ome.services.graphs.GraphStep;
import ome.services.messages.EventLogMessage;
import ome.services.sharing.ShareBean;
import ome.system.OmeroContext;
import ome.system.Roles;
import ome.tools.hibernate.ExtendedMetadata;
import ome.tools.hibernate.QueryBuilder;
import ome.tools.spring.InternalServiceFactory;
import ome.util.SqlAction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.Session;

/**
 * Post-processing action produced by {@link ChgrpStepFactory},
 * one for each {@link ChgrpStep} in order to check group permission
 * constraints.
 *
 * @author Josh Moore, josh at glencoesoftware.com
 * @since Beta4.3.2
 * @see ticket:6422
 */
public class ChgrpValidation extends GraphStep {

    final private static Log log = LogFactory.getLog(ChgrpValidation.class);

    final private OmeroContext ctx;

    final private ExtendedMetadata em;

    final private long userGroup;

    final private long grp;

    final private ShareBean share;

    public ChgrpValidation(OmeroContext ctx, ExtendedMetadata em, Roles roles,
            int idx, List<GraphStep> stack,
            GraphSpec spec, GraphEntry entry, long[] ids, long grp) {
        super(idx, stack, spec, entry, ids);
        this.ctx = ctx;
        this.em = em;
        this.grp = grp;
        this.userGroup = roles.getUserGroupId();
        this.share = (ShareBean) new InternalServiceFactory(ctx).getShareService();
    }

    @Override
    public void action(Callback cb, Session session, SqlAction sql, GraphOpts opts)
    throws GraphException {

        final QueryBuilder qb = queryBuilder(opts);
        qb.param("id", id);
        qb.param("grp", grp);
        Query q = qb.query(session);
        int count = q.executeUpdate();

        // ticket:6422 - validation of graph
        // =====================================================================
        // Immediately we check that an object moved from GroupA to GroupB
        // is no longer pointed at by any objects in GroupA via foreign key
        // constraints. This is what the DB does for us inherently on delete.
        //
        //
        // NB: After all objects are moved, we need to perform the reverse
        // check, which is that no object in GroupB points at any objects in
        // GroupA, i.e. all necessary objects were moved.
        final String[][] locks = em.getLockChecks(iObjectType);

        int total = 0;
        for (String[] lock : locks) {
            Long bad = findImproperLinks(session, lock);
            if (bad != null && bad > 0) {
                log.warn(String.format("%s:%s improperly linked by %s.%s: %s",
                        iObjectType.getSimpleName(), id, lock[2], lock[1],
                        bad));
                total += bad;
            }
        }
        if (total > 0) {
            throw new GraphException(String.format("%s:%s improperly linked by %s objects",
                    iObjectType.getSimpleName(), id, total));
        }


        if (count > 0) {
            cb.addGraphIds(this);
        }
        logResults(count);
    }

    public void validate(Callback cb, Session session, SqlAction sql, GraphOpts opts)
        throws GraphException {

    }

    private QueryBuilder queryBuilder(GraphOpts opts) {
        final QueryBuilder qb = new QueryBuilder();
        qb.update(table);
        qb.append("set group_id = :grp ");
        qb.where();
        qb.and("id = :id");
        if (!opts.isForce()) {
            permissionsClause(ec, qb);
        }
        return qb;
    }

    private Long findImproperLinks(Session session, String[] lock) {
        Long old = share.setShareId(-1L);
        try {
            Query q = session.createQuery(String.format(
                    "select count(*) from %s source where source.%s.id = ? and " +
                    "(source.details.group.id = ? OR source.details.group.id = ?)",
                    lock[0], lock[1]));
            q.setLong(0, id);
            q.setLong(1, grp);
            q.setLong(2, userGroup);

            return (Long) q.list().get(0);
        } finally {
            share.setShareId(old);
        }
    }

    @Override
    public void onRelease(Class<IObject> k, Set<Long> ids)
            throws GraphException {
        EventLogMessage elm = new EventLogMessage(this, "CHGRP", k,
                new ArrayList<Long>(ids));

        try {
            ctx.publishMessage(elm);
        } catch (Throwable t) {
            GraphException de = new GraphException("EventLogMessage failed.");
            de.initCause(t);
            throw de;
        }

    }

}
