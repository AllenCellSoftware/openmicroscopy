/*
 * org.openmicroscopy.shoola.env.data.views.calls.ClassificationLoader
 *
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */

package org.openmicroscopy.shoola.env.data.views.calls;


//Java imports
import java.util.HashSet;
import java.util.Set;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.env.data.OmeroDataService;
import org.openmicroscopy.shoola.env.data.views.BatchCall;
import org.openmicroscopy.shoola.env.data.views.BatchCallTree;
import pojos.CategoryGroupData;

/** 
 * Command to find the Category Group/Category paths that end or don't end with
 * a specified Image.
 * <p>This command can be created to load either all the Categories under which
 * a given Image was classified and all enclosing Category Groups or to do the
 * opposite &#151; to load all the Categories the given Image doesn't belong in,
 * and then all the Category Groups that contain those Categories.</p>
 * <p>The object returned in the <code>DSCallOutcomeEvent</code> will be a
 * <code>Set</code> with all Category Group nodes (as <code>CategoryGroupData
 * </code> objects) that were found.  Those objects will also be linked to the 
 * matching Categories (represented by <code>CategoryData</code> objects).</p>
 * 
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author  <br>Andrea Falconi &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:a.falconi@dundee.ac.uk">
 * 					a.falconi@dundee.ac.uk</a>
 * @version 2.2
 * <small>
 * (<b>Internal version:</b> $Revision$ $Date$)
 * </small>
 * @since OME2.2
 */
public class ClassificationLoader
    extends BatchCallTree
{
    
    /** Identifies the <code>Declassification</code> algorithm. */
    public static final int DECLASSIFICATION = 
                                OmeroDataService.DECLASSIFICATION;
    
    /**
     * Identifies the <code>Classification</code> algorithm with
     * mutually exclusive rule.
     */
    public static final int CLASSIFICATION_ME = 
                                OmeroDataService.CLASSIFICATION_ME;
    
    /**
     * Identifies the <code>Classification</code> algorithm without
     * mutually exclusive rule.
     */
    public static final int CLASSIFICATION_NME = 
                            	OmeroDataService.CLASSIFICATION_NME;
    
    /** The root nodes of the found trees. */
    private Set         rootNodes;
    
    /** Searches the CG/C/I hierarchy. */
    private BatchCall   loadCall;
     
    /**
     * Checks if the index specified is supported.
     * 
     * @param i The passed index.
     * @return 	Returns <code>true</code> if the index is supported,
     * 			<code>false</code> otherwise.
     */
    private boolean checkAlgorithmIndex(int i)
    {
        switch (i) {
            case DECLASSIFICATION:
            case CLASSIFICATION_ME:
            case CLASSIFICATION_NME:    
                return true;
            default: return false;
        }
    }
    
    /**
     * Creates a {@link BatchCall} to load all Category Group/Category paths
     * that don't end with the specified Image.
     * If bad arguments are passed, we throw a runtime
	 * exception so to fail early and in the caller's thread.
     * 
     * @param imageIDs      The set of image ids.
     * @param algorithm     One out of the following constants:
     *                      {@link #DECLASSIFICATION},
     *                      {@link #CLASSIFICATION_ME},
     *                      {@link #CLASSIFICATION_NME}.
     * @param rootLevel     The level of the hierarchy either 
     *                      <code>GroupData</code> or 
     *                      <code>ExperimenterData</code>.
     * @param rootLevelID   The Id of the root.                  
     * @return The {@link BatchCall}.
     */
    private BatchCall loadCGCPaths(final Set imageIDs, final int algorithm, 
            final Class rootLevel, final long rootLevelID)
    {
        return new BatchCall("Loading CGC paths. ") {
            public void doCall() throws Exception
            {
                OmeroDataService os = context.getDataService();
                if (algorithm == CLASSIFICATION_NME)
                    rootNodes = os.loadContainerHierarchy(
                            CategoryGroupData.class, null, false,
                            rootLevel, rootLevelID);
                else 
                    rootNodes = os.findCGCPaths(imageIDs, algorithm, rootLevel, 
                                            rootLevelID);
            }
        };
    }
    
    /**
     * Adds the {@link #loadCall} to the computation tree.
     * @see BatchCallTree#buildTree()
     */
    protected void buildTree() { add(loadCall); }

    /**
     * Returns, in a <code>Set</code>, the root nodes of the found trees.
     * @see BatchCallTree#getResult()
     */
    protected Object getResult() { return rootNodes; }

    /**
     * Creates a new instance to find the Category Group/Category paths that 
     * end or don't end with the specified Image.
     * If bad arguments are passed, we throw a runtime exception so to fail
     * early and in the caller's thread.
     * 
     * @param imageID       The id of the Image to classify or declassifiy
     *                      depending on the algorithm.
     * @param algorithm     One out of the following constants:
     *                      {@link #DECLASSIFICATION},
     *                      {@link #CLASSIFICATION_ME},
     *                      {@link #CLASSIFICATION_NME}.
     * @param rootLevel     The level of the hierarchy either 
     *                      <code>GroupData</code> or 
     *                      <code>ExperimenterData</code>.
     * @param rootLevelID   The Id of the root.                    
     */
    public ClassificationLoader(long imageID, int algorithm, Class rootLevel, 
                                long rootLevelID)
    {
        if (imageID < 0) 
            throw new IllegalArgumentException("image ID not valid ");
        if (!checkAlgorithmIndex(algorithm))
            throw new IllegalArgumentException("Algorithm not supported.");
        Set<Long> set = new HashSet<Long>(1);
        set.add(new Long(imageID));
        loadCall  = loadCGCPaths(set, algorithm, rootLevel, rootLevelID);
    }
    
    /**
     * Creates a new instance to find the Category Group/Category paths that 
     * end or don't end with the specified Image.
     * If bad arguments are passed, we throw a runtime exception so to fail
     * early and in the caller's thread.
     * 
     * @param imageIDs      The collection of image's ids.
     * @param algorithm     One of the following constants:
     *                      {@link #DECLASSIFICATION},
     *                      {@link #CLASSIFICATION_ME},
     *                      {@link #CLASSIFICATION_NME}.
     * @param rootLevel     The level of the hierarchy either 
     *                      <code>GroupData</code> or 
     *                      <code>ExperimenterData</code>.
     * @param rootLevelID   The Id of the root.                
     */
    public ClassificationLoader(Set imageIDs, int algorithm, Class rootLevel, 
                                long rootLevelID)
    {
        if ((imageIDs == null || imageIDs.size() == 0) && 
        		algorithm == DECLASSIFICATION)
            throw new IllegalArgumentException("The collection of ids" +
                    "cannot be null or of size 0.");
        if (!checkAlgorithmIndex(algorithm))
            throw new IllegalArgumentException("Algorithm not supported.");
        loadCall  = loadCGCPaths(imageIDs, algorithm, rootLevel, rootLevelID);
    }
    
}
