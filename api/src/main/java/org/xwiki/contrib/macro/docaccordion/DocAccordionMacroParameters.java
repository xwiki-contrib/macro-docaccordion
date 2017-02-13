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
package org.xwiki.contrib.macro.docaccordion;

import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyMandatory;
import org.xwiki.properties.annotation.PropertyName;

import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.SpaceReference;

/**
 * Parameters for the {@link org.xwiki.contrib.macro.docaccordion.internal.DocAccordionMacro} Macro.
 */
public class DocAccordionMacroParameters
{
    public enum DocAccordionMacroSort
    {
        /**
         * Accordions sorted chronologically.
         */
        CHRONO,

        /**
         * Accordions sorted alphabetically.
         */
        ALPHA;
    }

    /**
     * @see {@link #getSpace()}
     */
    private String space;

    /**
     * @see {@link #getXClass()}
     */
    private String xclass;

    /**
     * @see {@link #getSort()}
     */
    private DocAccordionMacroSort sort = DocAccordionMacroSort.CHRONO;

    /**
     * @see {@link #getDisplayAuthor()}
     */
    private boolean displayAuthor = true;

    /**
     * @see {@link #getDisplayDate()}
     */
    private boolean displayDate = true;

    /**
     * @see {@link #getLimit()}
     */
    private int limit = 100;

    /**
     * @see {@link #getAccordionMaxHeight()}
     */
    private int accordionMaxHeight;

    /**
     * @see {@link #getOpenFirstAccordion()}
     */
    private boolean openFirstAccordion = true;

    /**
     * @return the space parameter
     */
    public String getSpace()
    {
        return this.space;
    }

    /**
     * @param space the space parameter
     */
    @PropertyName("Space")
    @PropertyDescription("Reference to a space in the current wiki.")
    public void setSpace(String space)
    {
        this.space = space;
    }

    /**
     * @return the xclass parameter
     */
    public String getXClass()
    {
        return this.xclass;
    }

    /**
     * @param xclass the xclass parameter
     */
    @PropertyName("XClass")
    @PropertyDescription("reference to a class in the current wiki. All document of that xclass will be used for this accordion.")
    public void setXClass(String xclass)
    {
        this.xclass = xclass;
    }

    /**
     * @return the sort parameter
     */
    public DocAccordionMacroSort getSort()
    {
        return this.sort;
    }

    /**
     * @param sort the sort parameter
     */
    @PropertyName("Sorting")
    @PropertyDescription("Defines how to sort the accordions, chronologically (most recent to oldest) or alphabetically (sort title from A to Z)")
    public void setSort(DocAccordionMacroSort sort)
    {
        this.sort = sort;
    }

    /**
     * @return the displayAuthor parameter
     */
    public boolean getDisplayAuthor()
    {
        return this.displayAuthor;
    }

    /**
     * @param displayAuthor the displayAuthor parameter
     */
    @PropertyName("Display the author")
    @PropertyDescription("Display the document author. Displayed by default.")
    public void setDisplayAuthor(boolean displayAuthor)
    {
        this.displayAuthor = displayAuthor;
    }

    /**
     * @return the displayDate parameter
     */
    public boolean getDisplayDate()
    {
        return this.displayDate;
    }

    /**
     * @param displayDate the displayDate parameter
     */
    @PropertyName("Display the modification date")
    @PropertyDescription("Display the document last modification date. Displayed by default.")
    public void setDisplayDate(boolean displayDate)
    {
        this.displayDate = displayDate;
    }

    /**
     * @return the limit parameter
     */
    public int getLimit()
    {
        return this.limit;
    }

    /**
     * @param limit the limit parameter
     */
    @PropertyName("Number of accordions to display")
    @PropertyDescription("Number of accordions to display. 100 accordions displayed by default.")
    public void setLimit(int limit)
    {
        this.limit = limit;
    }

    /**
     * @return the accordionMaxHeight parameter
     */
    public int getAccordionMaxHeight()
    {
        return this.accordionMaxHeight;
    }

    /**
     * @param accordionMaxHeight the accordionMaxHeight parameter
     */
    @PropertyName("The maximum height of an accordion")
    @PropertyDescription("The max height of a given accordion block")
    public void setAccordionMaxHeight(int accordionMaxHeight)
    {
        this.accordionMaxHeight = accordionMaxHeight;
    }

    /**
     * @return the openFirstAccordion parameter
     */
    public boolean getOpenFirstAccordion()
    {
        return this.openFirstAccordion;
    }

    /**
     * @param openFirstAccordion the openFirstAccordion parameter
     */
    @PropertyName("Open the first accordion")
    @PropertyDescription("Open the first accordion. Displayed by default.")
    public void setOpenFirstAccordion(boolean openFirstAccordion)
    {
        this.openFirstAccordion = openFirstAccordion;
    }
}
