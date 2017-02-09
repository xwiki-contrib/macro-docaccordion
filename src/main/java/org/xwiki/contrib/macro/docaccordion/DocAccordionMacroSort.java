package org.xwiki.contrib.macro.docaccordion;

/**
 * Enumerates the DocAccordion macro sort options.
 * 
 * @version $Id$
 */
public enum DocAccordionMacroSort
{
    CHRONO("CHRONO"),

    ALPHA("ALPHA");

    private String sortAsString;

    private DocAccordionMacroSort(String sortAsString)
    {
        this.sortAsString = sortAsString;
    }

    @Override
    public String toString()
    {
        return this.sortAsString;
    }
}
