from django.conf.urls.defaults import *
from django.views.static import serve

from omeroweb.webtest import views

import os

urlpatterns = patterns('django.views.generic.simple',

    # index 'home page' of the webtest app
    url( r'^$', views.index, name='webtest_index' ),

    # 'Hello World' example from tutorial on http://trac.openmicroscopy.org.uk/ome/wiki/OmeroWeb
    url( r'^dataset/(?P<datasetId>[0-9]+)/$', views.dataset, name="webtest_dataset" ),

    # Displays images (one per row) one channel per column in a grid. Params are passed in request, E.g. imageIds
    url( r'^split_view_figure/$', views.split_view_figure, name="webtest_split_view_figure"),

    # View a dataset as two panels of images, each with a different rendering setting
    url( r'^dataset_split_view/(?P<datasetId>[0-9]+)/', views.dataset_split_view, name='webtest_dataset_split_view' ),

    # view an image in grid with the Z, C, T dimensions split over the x or y axes as chosen by user.
    url( r'^image_dimensions/(?P<imageId>[0-9]+)/', views.image_dimensions, name='webtest_image_dimensions' ),

    # Viewer overlays individual channels from the same image (or different images) and manipulate them separately..
    # translate, scale etc relative to one-another.
    url( r'^channel_overlay_viewer/(?P<imageId>[0-9]+)/', views.channel_overlay_viewer, name='webtest_channel_overlay_viewer' ),
    # this is the url for rendering planes for the viewer
    url( r'^render_channel_overlay/', views.render_channel_overlay, name='webtest_render_channel_overlay' ),

    # post a comment annotation to images. parameters are in request: imageIds=123,234  comment=blah 
    # ns=Namespace replace=true (replaces existing comment with same ns if found)
    url( r'^add_annotations/$', views.add_annotations, name="webtest_add_annotations"),
    
    # examples of using the 'common' templates
    url(r'^common/(?P<base_template>[a-z0-9_]+)/', views.common_templates, name='common'),
)
