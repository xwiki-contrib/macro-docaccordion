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

  $('.xwiki-accordion .collapse').on('hide.bs.collapse', function () {
    var panel = $(this);
    var scrollTop = Math.max( $("html").scrollTop(), $("body").scrollTop());
    if (scrollTop > panel.offset().top) {
      $('html, body').scrollTop(panel.offset().top);
    }
    panel.collapse({toggle: true});
  });

  // Load the first accordions
  $(".panel-heading.openFirstAccordion a").click();
});