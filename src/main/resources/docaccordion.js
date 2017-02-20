require(['jquery', 'bootstrap'], function($){
  function loadAccordion (accordionElement) {
    if (!accordionElement.hasClass("content-already-loaded")) {
      var accordionBody = $(".panel-body .xwiki-accordion-content", accordionElement );
      accordionBody.addClass("loading");
	  accordionBody.html("&nbsp;");
      var docURL = accordionElement.parent().find("a").attr("rel");
      accordionBody.load( docURL , function(){
        accordionBody.removeClass("loading");
        accordionElement.addClass("content-already-loaded");
        $(".xwiki-accordion-footer", accordionElement).show();
      });
    }
  }

  // Initialize accordions
  $('.xwiki-accordion .collapse').on('show.bs.collapse', function () {
    loadAccordion($(this));
  });

  // Load the first accordions
  $(".panel-heading.openFirstAccordion a").click();

});