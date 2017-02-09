/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.macro.docaccordion.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang.RandomStringUtils;

import org.slf4j.Logger;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import org.xwiki.contrib.macro.docaccordion.DocAccordionMacroParameters;
import org.xwiki.contrib.macro.docaccordion.DocAccordionMacroSort;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

/**
 * DocAccordion Macro.
 */
@Component
@Named("docaccordion")
public class DocAccordionMacro extends AbstractMacro<DocAccordionMacroParameters>
{
    /**
     * The description of the macro.
     */
    private static final String DESCRIPTION = "Rendering macro for displaying multiple documents as an accordion";

    private static final String AWM_LIVE_TABLE_CLASS = "AppWithinMinutes.LiveTableClass";

    private static final int MAX_ACCORDIONS_TO_DISPLAY = 100;

    @Inject
    private QueryManager queryManager;

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    /**
     * Create and initialize the descriptor of the macro.
     */
    public DocAccordionMacro()
    {
        super("DocAccordion", DESCRIPTION, DocAccordionMacroParameters.class);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.rendering.macro.Macro#execute(Object, String, MacroTransformationContext)
     */
    public List<Block> execute(DocAccordionMacroParameters parameters, String content,
        MacroTransformationContext transformationContext) throws MacroExecutionException
    {
        List<Block> result = new ArrayList<Block>();

        // Build the query
        String sourceXClass = null;

        if (parameters.getXClass() != null) {
            sourceXClass = parameters.getXClass();
        } else {
            if (parameters.getSpace() != null) {
                sourceXClass = getSpaceAWMDataXClass(parameters.getSpace());
            }
        }

        if (!StringUtils.isBlank(sourceXClass)) {
            try {
                List<String> accordionsStringReferences =
                    getAccordions(parameters.getSpace(), sourceXClass, parameters.getSort(), parameters.getLimit());
                result = generateAccordionBlocks(accordionsStringReferences, parameters, transformationContext);
            } catch (Exception e) {
                logger.error(
                    "An error appears when trying to get accordions for the parameters [space: {}, xclass: {}, sort: {}, limit: {}]",
                    parameters.getSpace(), sourceXClass, parameters.getSort(), parameters.getLimit(), e);
            }
        } else {
            result.add(new WordBlock("Bad combination space, xclass"));
        }

        return result;
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.rendering.macro.Macro#supportsInlineMode()
     */
    public boolean supportsInlineMode()
    {
        return false;
    }

    /**
     * Get the Data XClass of the AWM application installed on a given space
     * 
     * @param space
     * @return the AWP Data XClass reference or null if the space does not contains an AWM
     */
    private String getSpaceAWMDataXClass(String space)
    {
        String awmDataXclass = null;

        try {
            StringBuilder xwql = new StringBuilder("SELECT awm.class");
            xwql.append(String.format(" FROM Document doc, doc.object(%s) AS awm", AWM_LIVE_TABLE_CLASS));
            xwql.append(" WHERE");
            xwql.append(" doc.space=:space");
            Query query = queryManager.createQuery(xwql.toString(), Query.XWQL);
            query.bindValue("space", space);
            List<String> results = query.setLimit(1).execute();
            if (results.size() > 0) {
                awmDataXclass = results.get(0);
            }
        } catch (Exception e) {
            logger.error("An error appears when trying to get the AWP data XClass in the space {}", space, e);
        }

        return awmDataXclass;
    }

    private List<String> getAccordions(String sourceSpace, String sourceXClass, DocAccordionMacroSort sort, int limit)
        throws Exception
    {
        List<String> results = new ArrayList<>();

        // Generate the query
        StringBuilder xwql = new StringBuilder(String.format("FROM doc.object(%s) AS sourceObj", sourceXClass));
        xwql.append(" WHERE");

        // Filter by space
        if (!StringUtils.isBlank(sourceSpace)) {
            xwql.append(" doc.space=:space");
        }

        // Sort results
        String orderBy = " ORDER BY doc.creationDate DESC";
        if (DocAccordionMacroSort.ALPHA.equals(sort)) {
            orderBy = " ORDER BY doc.title";
        }

        xwql.append(orderBy);

        Query query = queryManager.createQuery(xwql.toString(), Query.XWQL);

        if (!StringUtils.isBlank(sourceSpace)) {
            query.bindValue("space", sourceSpace);
        }

        results = query.execute();

        // Set limits
        int queryLimit = MAX_ACCORDIONS_TO_DISPLAY;
        if (limit < queryLimit) {
            queryLimit = limit;
        }

        List<String> authorizedResults = new ArrayList<>();

        for (String docFullName : results) {
            DocumentReference documentReference = resolver.resolve(docFullName);

            if (authorizedResults.size() == queryLimit) {
                break;
            }

            if (authorizationManager.hasAccess(Right.VIEW, documentReference)) {
                authorizedResults.add(docFullName);
            }
        }

        return authorizedResults;
    }

    private List<Block> generateAccordionBlocks(List<String> accordionsStringReferences,
        DocAccordionMacroParameters parameters, MacroTransformationContext transformationContext)
    {
        List<Block> result = new ArrayList<Block>();

        XWikiContext xcontext = contextProvider.get();
        XWiki xwiki = xcontext.getWiki();

        // Top container block
        Map<String, String> topContainerBlockParams = new HashMap<>();
        topContainerBlockParams.put("class", "panel-group xwiki-accordion");
        topContainerBlockParams.put("role", "tablist");
        topContainerBlockParams.put("aria-multiselectable", "true");
        String topContainerBlockIdSuffix = RandomStringUtils.random(6, true, true);
        topContainerBlockParams.put("id", String.format("accordion%s", topContainerBlockIdSuffix));
        GroupBlock topContainerBlock = new GroupBlock(new ArrayList<Block>(), topContainerBlockParams);

        // Accordions blocks
        for (int i = 0; i < accordionsStringReferences.size(); i++) {
            String accordionFullName = accordionsStringReferences.get(i);

            try {
                XWikiDocument accordionItemDoc = xwiki.getDocument(resolver.resolve(accordionFullName), xcontext);
                String title = accordionItemDoc.getRenderedTitle(transformationContext.getSyntax(), xcontext);

                // Accordion item block
                Map<String, String> accordionItemBlockParams = new HashMap<>();
                accordionItemBlockParams.put("class", "panel panel-default");
                GroupBlock accordionItemBlock = new GroupBlock(new ArrayList<Block>(), accordionItemBlockParams);

                // Accordion item Panel heading
                Map<String, String> accordionItemPanelHeadingParams = new HashMap<>();
                String accordionItemIdSuffix = RandomStringUtils.random(6, true, true);
                accordionItemPanelHeadingParams.put("class", "panel-heading");
                accordionItemPanelHeadingParams.put("id", String.format("accordionHeading%s", accordionItemIdSuffix));
                GroupBlock accordionItemPanelHeading =
                    new GroupBlock(new ArrayList<Block>(), accordionItemPanelHeadingParams);

                // Accordion item Panel heading title
                Map<String, String> accordionItemPanelHeadingTitleParams = new HashMap<>();
                accordionItemPanelHeadingTitleParams.put("class", "panel-title");
                HeaderBlock accordionItemPanelHeadingTitle =
                    new HeaderBlock(new ArrayList<Block>(), HeaderLevel.LEVEL4, accordionItemPanelHeadingTitleParams);

                // Accordion item Panel heading title link
                Map<String, String> accordionItemPanelHeadingTitleLinkParams = new HashMap<>();
                accordionItemPanelHeadingTitleLinkParams.put("role", "button");
                accordionItemPanelHeadingTitleLinkParams.put("data-toggle", "collapse");
                accordionItemPanelHeadingTitleLinkParams.put("data-parent",
                    String.format("#accordion%s", topContainerBlockIdSuffix));
                accordionItemPanelHeadingTitleLinkParams.put("aria-expanded", "true");
                accordionItemPanelHeadingTitleLinkParams.put("aria-controls",
                    String.format("collapse%s", accordionItemIdSuffix));
                accordionItemPanelHeadingTitleLinkParams.put("rel", accordionItemDoc.getURL("get", xcontext));
                ResourceReference ressourceReference =
                    new ResourceReference(String.format("#collapse%s", accordionItemIdSuffix), ResourceType.URL);
                LinkBlock accordionItemPanelHeadingTitleLink = new LinkBlock(Arrays.<Block>asList(new WordBlock(title)),
                    ressourceReference, true, accordionItemPanelHeadingTitleLinkParams);

                accordionItemPanelHeadingTitle.addChild(accordionItemPanelHeadingTitleLink);

                accordionItemPanelHeading.addChild(accordionItemPanelHeadingTitle);

                accordionItemBlock.addChild(accordionItemPanelHeading);

                // Accordion item Panel collapse
                String content = "";
                Map<String, String> accordionItemPanelCollapseParams = new HashMap<>();
                accordionItemPanelCollapseParams.put("class", "panel-collapse collapse");
                accordionItemPanelCollapseParams.put("id", String.format("collapse%s", accordionItemIdSuffix));
                accordionItemPanelCollapseParams.put("role", "tabpanel");
                accordionItemPanelCollapseParams.put("aria-labelledby",
                    String.format("heading%s", accordionItemIdSuffix));
                GroupBlock accordionItemPanelCollapse =
                    new GroupBlock(new ArrayList<Block>(), accordionItemPanelCollapseParams);

                // Accordion item Panel collapse body
                Map<String, String> accordionItemPanelCollapseBodyParams = new HashMap<>();
                accordionItemPanelCollapseBodyParams.put("class", "panel-body");
                GroupBlock accordionItemPanelCollapseBody =
                    new GroupBlock(Arrays.<Block>asList(new WordBlock(content)), accordionItemPanelCollapseBodyParams);

                accordionItemPanelCollapse.addChild(accordionItemPanelCollapseBody);

                accordionItemBlock.addChild(accordionItemPanelCollapse);

                // Add the block to the top container block
                topContainerBlock.addChild(accordionItemBlock);
            } catch (Exception e) {
                logger.error("An error appears when trying to generate the XDOM block for the document: {}",
                    accordionFullName, e);
            }
        }
        result.add(topContainerBlock);

        return result;
    }
}
