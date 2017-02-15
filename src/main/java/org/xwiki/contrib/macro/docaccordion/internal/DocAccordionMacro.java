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
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang.RandomStringUtils;

import org.slf4j.Logger;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.contrib.macro.docaccordion.DocAccordionMacroParameters;
import org.xwiki.contrib.macro.docaccordion.DocAccordionMacroParameters.DocAccordionMacroSort;
import org.xwiki.component.annotation.Component;
import org.xwiki.localization.LocalizationManager;
import org.xwiki.localization.Translation;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceResolver;
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

    private static final int DEFAULT_ACCORDIONS_TO_DISPLAY = 100;

    private static final int MIN_QUERY_LIMIT = 200;

    private static final int MAX_QUERY_LIMIT = 1000;

    private static final String APPLICATIONS_TRANSLATIONS_PREFIX = "rendering.macro.docaccordion.application.";

    @Inject
    private QueryManager queryManager;

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private EntityReferenceResolver<String> entityReferenceResolver;

    @Inject
    private ContextualAuthorizationManager authorizationManager;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Inject
    private LocalizationManager localization;

    @Inject
    @Named("jsrx")
    private SkinExtension jsrxSkinExtension;

    @Inject
    @Named("ssrx")
    private SkinExtension ssrxSkinExtension;

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

        try {

            XWikiContext xcontext = contextProvider.get();
            XWiki xwiki = xcontext.getWiki();

            // Resolve the xclass and space
            DocumentReference xclassReference = null;

            if (!StringUtils.isBlank(parameters.getXclass())) {
                // Check if the xclass is an application name and replace it by the its corresponding real data xclass
                Translation translation = localization.getTranslation(
                    String.format("%s%s", APPLICATIONS_TRANSLATIONS_PREFIX, parameters.getXclass().toLowerCase()),
                    contextProvider.get().getLocale());
                if (translation != null) {
                    parameters.setXclass(translation.getRawSource().toString());
                }

                xclassReference = documentReferenceResolver.resolve(parameters.getXclass());
            }

            SpaceReference spaceReference =
                new SpaceReference(entityReferenceResolver.resolve(parameters.getSpace(), EntityType.SPACE));

            if (xclassReference == null || !xwiki.exists(xclassReference, xcontext)) {
                xclassReference = getSpaceAWMDataXClass(spaceReference);
                // Starting from xwiki 8.4.4 AWP data can be added on multiple spaces, to cover this case we will not
                // filter results by space in the case of AWM documents
                parameters.setSpace("");
            }

            if (xclassReference != null) {
                try {
                    List<String> accordionsStringReferences =
                        getAccordions(spaceReference, xclassReference, parameters);
                    result = generateAccordionBlocks(accordionsStringReferences, parameters, transformationContext);
                } catch (Exception e) {
                    throw new MacroExecutionException(String.format(
                        "An error appears when trying to get accordions for the parameters [space: %s, xclass: %s, sort: %s, limit: %s], reason: %s",
                        parameters.getSpace(), xclassReference, parameters.getSort(), parameters.getLimit(),
                        e.getMessage()));
                }
            } else {
                throw new MacroExecutionException(localization
                    .getTranslation("rendering.macro.docaccordion.wrong_parameters", contextProvider.get().getLocale())
                    .getRawSource().toString());
            }

        } catch (XWikiException xe) {
            throw new MacroExecutionException(xe.getMessage());
        }

        // Inject JS/CSS helper scripts
        jsrxSkinExtension.use("docaccordion.js");
        ssrxSkinExtension.use("docaccordion.css");

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
     * Get the Data XClass reference of the AWM application installed on a given space reference
     * 
     * @param spaceReference the space reference
     * @return the AWP Data XClass reference or null if the space does not contains an AWM
     */
    private DocumentReference getSpaceAWMDataXClass(SpaceReference spaceReference) throws XWikiException
    {
        DocumentReference awmDataXClassReference = null;

        XWikiContext xcontext = contextProvider.get();
        XWiki xwiki = xcontext.getWiki();

        DocumentReference awmMainPageReference = new DocumentReference("WebHome", spaceReference);

        if (xwiki.exists(awmMainPageReference, xcontext)) {
            XWikiDocument awmMainDocument = xwiki.getDocument(awmMainPageReference, xcontext);
            BaseObject awmObj = awmMainDocument.getXObject(documentReferenceResolver.resolve(AWM_LIVE_TABLE_CLASS));
            if (awmObj != null) {
                awmDataXClassReference = documentReferenceResolver.resolve(awmObj.getStringValue("class"));
            }
        }
        return awmDataXClassReference;
    }

    private List<String> getAccordions(SpaceReference spaceReference, DocumentReference xclassReference,
        DocAccordionMacroParameters parameters) throws Exception
    {
        List<String> authorizedResults = new ArrayList<>();

        // Generate the query
        StringBuilder xwql = new StringBuilder(
            String.format("FROM doc.object(%s) AS sourceObj", localSerializer.serialize(xclassReference)));

        // Filter by space
        if (!StringUtils.isBlank(parameters.getSpace())) {
            xwql.append(" WHERE");
            xwql.append(" doc.fullName LIKE :space");
            xwql.append(" escape '!'");// Added to fix a pitfall on mysql when we have spaces with points '.'
        }

        // Sort results
        String orderBy = " ORDER BY doc.date DESC";
        if (DocAccordionMacroSort.ALPHA.equals(parameters.getSort())) {
            orderBy = " ORDER BY doc.title";
        }

        xwql.append(orderBy);

        Query query = queryManager.createQuery(xwql.toString(), Query.XWQL);

        if (!StringUtils.isBlank(parameters.getSpace())) {
            // Added to fix a pitfall on mysql when we have spaces with points '.'
            String spaceLike = localSerializer.serialize(spaceReference).replaceAll("([%_!])", "!$1").concat(".%");
            query.bindValue("space", spaceLike);
        }

        // Manage the limit parameter
        int queryLimit = MIN_QUERY_LIMIT;
        if (parameters.getLimit() > DEFAULT_ACCORDIONS_TO_DISPLAY) {
            queryLimit = MAX_QUERY_LIMIT;
        }

        query.setLimit(queryLimit);

        int offset = 0;
        boolean stop = false;

        do {
            query.setOffset(offset);
            List<String> results = query.execute();
            for (String docFullName : results) {
                DocumentReference documentReference = documentReferenceResolver.resolve(docFullName);

                if (authorizedResults.size() == parameters.getLimit()) {
                    break;
                }

                if (authorizationManager.hasAccess(Right.VIEW, documentReference)) {
                    authorizedResults.add(docFullName);
                }
            }

            if ((authorizedResults.size() == parameters.getLimit()) || (results.size() < queryLimit)) {
                stop = true;
            }

            offset = offset + queryLimit;

        } while (!stop);

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
                XWikiDocument accordionItemDoc =
                    xwiki.getDocument(documentReferenceResolver.resolve(accordionFullName), xcontext);
                String title = accordionItemDoc.getRenderedTitle(transformationContext.getSyntax(), xcontext);

                // Accordion item block
                Map<String, String> accordionItemBlockParams = new HashMap<>();
                accordionItemBlockParams.put("class", "panel panel-default");
                GroupBlock accordionItemBlock = new GroupBlock(new ArrayList<Block>(), accordionItemBlockParams);

                // Accordion item Panel heading
                Map<String, String> accordionItemPanelHeadingParams = new HashMap<>();
                String accordionItemIdSuffix = RandomStringUtils.random(6, true, true);
                String cssClasses = "";
                if (parameters.getOpenFirstAccordion() && i == 0) {
                    cssClasses = "openFirstAccordion";
                }
                accordionItemPanelHeadingParams.put("class", String.format("panel-heading %s", cssClasses));
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
                    new ResourceReference(String.format("#collapse%s", accordionItemIdSuffix), ResourceType.PATH);
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
                // Check if the max. is applied
                if (parameters.getAccordionMaxHeight() > 0) {
                    accordionItemPanelCollapseBodyParams.put("style",
                        String.format("overflow: scroll;max-height: %spx", parameters.getAccordionMaxHeight()));
                }
                GroupBlock accordionItemPanelCollapseBody =
                    new GroupBlock(new ArrayList<Block>(), accordionItemPanelCollapseBodyParams);

                // Enclose the accordion content with a div
                Map<String, String> accordionItemPanelCollapseBodyContentParams = new HashMap<>();
                accordionItemPanelCollapseBodyContentParams.put("class", "xwiki-accordion-content");
                GroupBlock accordionItemPanelCollapseBodyContent = new GroupBlock(
                    Arrays.<Block>asList(new WordBlock(content)), accordionItemPanelCollapseBodyContentParams);

                accordionItemPanelCollapseBody.addChild(accordionItemPanelCollapseBodyContent);

                // Add the footer panel to the accordion content (The footer will contain the author + modification
                // date)
                if (parameters.getDisplayAuthor() || parameters.getDisplayDate()) {
                    String author = "";
                    if (parameters.getDisplayAuthor()) {
                        author = xwiki.getUserName(localSerializer.serialize(accordionItemDoc.getAuthorReference()),
                            "$first_name $last_name", false, xcontext);
                    }

                    String date = "";
                    if (parameters.getDisplayDate()) {
                        date = xwiki.formatDate(accordionItemDoc.getDate(), "dd MMMM yyyy", xcontext);
                    }

                    // Generate the footer text
                    Locale locale = contextProvider.get().getLocale();
                    Translation translation1 =
                        localization.getTranslation("rendering.macro.docaccordion.footer.modified", locale);
                    Translation translation2 =
                        localization.getTranslation("rendering.macro.docaccordion.footer.by", locale);
                    Translation translation3 =
                        localization.getTranslation("rendering.macro.docaccordion.footer.on", locale);
                    String footerContent = "";

                    if (!StringUtils.isBlank(author) && !StringUtils.isBlank(date)) {
                        footerContent = String.format("%s %s %s, %s %s", translation1.getRawSource().toString(),
                            translation2.getRawSource().toString(), author, translation3.getRawSource().toString(),
                            date);
                    } else if (!StringUtils.isBlank(author)) {
                        footerContent = String.format("%s %s %s", translation1.getRawSource().toString(),
                            translation2.getRawSource().toString(), author);
                    } else {
                        footerContent = String.format("%s %s %s", translation1.getRawSource().toString(),
                            translation3.getRawSource().toString(), date);
                    }

                    Map<String, String> accordionItemPanelCollapseFooterParams = new HashMap<>();
                    accordionItemPanelCollapseFooterParams.put("class", "text-muted text-right xwiki-accordion-footer");
                    GroupBlock accordionItemPanelCollapseFooter = new GroupBlock(
                        Arrays.<Block>asList(new WordBlock(footerContent)), accordionItemPanelCollapseFooterParams);

                    accordionItemPanelCollapseBody.addChild(accordionItemPanelCollapseFooter);
                }

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
