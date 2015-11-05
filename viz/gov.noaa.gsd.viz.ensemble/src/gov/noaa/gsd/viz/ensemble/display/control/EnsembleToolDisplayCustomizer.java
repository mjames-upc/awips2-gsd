package gov.noaa.gsd.viz.ensemble.display.control;

import gov.noaa.gsd.viz.ensemble.display.rsc.GeneratedEnsembleGridResource;
import gov.noaa.gsd.viz.ensemble.display.rsc.histogram.HistogramResource;
import gov.noaa.gsd.viz.ensemble.display.rsc.timeseries.GeneratedTimeSeriesResource;
import gov.noaa.gsd.viz.ensemble.navigator.ui.layer.EnsembleTool;
import gov.noaa.gsd.viz.ensemble.navigator.ui.layer.EnsembleToolLayer;

import java.util.ArrayList;
import java.util.List;

import com.raytheon.uf.common.status.IUFStatusHandler;
import com.raytheon.uf.common.status.UFStatus;
import com.raytheon.uf.viz.core.drawables.IDescriptor;
import com.raytheon.uf.viz.core.drawables.IRenderableDisplay;
import com.raytheon.uf.viz.core.drawables.ResourcePair;
import com.raytheon.uf.viz.core.exception.VizException;
import com.raytheon.uf.viz.core.rsc.AbstractVizResource;
import com.raytheon.uf.viz.core.rsc.AbstractVizResource.ResourceStatus;
import com.raytheon.uf.viz.core.rsc.DisplayType;
import com.raytheon.uf.viz.core.rsc.IInitListener;
import com.raytheon.uf.viz.core.rsc.ResourceList;
import com.raytheon.uf.viz.core.rsc.ResourceList.AddListener;
import com.raytheon.uf.viz.core.rsc.ResourceList.RemoveListener;
import com.raytheon.uf.viz.core.rsc.capabilities.EditableCapability;
import com.raytheon.uf.viz.xy.timeseries.rsc.TimeSeriesResource;
import com.raytheon.viz.grid.rsc.general.D2DGridResource;
import com.raytheon.viz.grid.rsc.general.GridResource;
import com.raytheon.viz.ui.perspectives.IRenderableDisplayCustomizer;

/**
 * The ensemble tool renderable display customizer classes contain the event
 * handlers that keeps track of when new displays are created or removed, and
 * listens also for when resources get added or removed from those displays.
 * 
 * TODO: We need verify whether we need only keep track of displays that are
 * created when the ensemble tool is "on". This is problematic for the very
 * first display because the ensemble tool would like to "know" of that display
 * but is not "on" by default. So how do we know that the new display is the
 * very first display created (i.e. initial "Map" editor display).
 * 
 * <pre>
 * 
 * SOFTWARE HISTORY
 * 
 * Date         Ticket#    Engineer          Description
 * ------------ ---------- -----------    --------------------------
 * Dec 9, 2014    5056    epolster jing      Initial creation
 * Oct 30, 2015   12863    polster        clear all data members at close
 * 
 * </pre>
 * 
 * @author polster
 * @author jing
 * @version 1.0
 */
public class EnsembleToolDisplayCustomizer implements
        IRenderableDisplayCustomizer {

    private final transient IUFStatusHandler statusHandler = UFStatus
            .getHandler(EnsembleToolDisplayCustomizer.class);

    /* List of listeners we have for renderable displays */
    private final List<EnsembleToolRscLoadListener> listeners = new ArrayList<EnsembleToolRscLoadListener>();

    @Override
    public void customizeDisplay(IRenderableDisplay display) {

        boolean add = true;

        for (EnsembleToolRscLoadListener listener : listeners) {
            if (display == listener.getDisplay()) {
                add = false;
                break;
            }
        }

        if (add) {
            listeners.add(new EnsembleToolRscLoadListener(display));
        }

        /*
         * If the ensemble tool is running, and if the active editor has an
         * ensemble tool layer in "editable on" state, then let the ensemble
         * tool know to prepare for a the new editor so it can pull in any
         * incoming resources into it.
         */
        if (EnsembleTool.isToolRunning()) {
            if (EnsembleTool.getInstance().isToolEditable()) {
                EnsembleTool.getInstance().prepareForNewEditor();
            }
        }
    }

    @Override
    public void uncustomizeDisplay(IRenderableDisplay display) {

        EnsembleToolRscLoadListener toRemove = null;
        for (EnsembleToolRscLoadListener listener : listeners) {
            if (listener.getDisplay() == display) {
                toRemove = listener;
                break;
            }
        }
        if (toRemove != null) {
            toRemove.dispose();
            listeners.remove(toRemove);
        }
    }

    private static class EnsembleToolRscLoadListener implements AddListener,
            RemoveListener, IInitListener {

        // the display we are listening to
        private final IRenderableDisplay display;

        public EnsembleToolRscLoadListener(IRenderableDisplay display) {
            this.display = display;
            IDescriptor descriptor = display.getDescriptor();
            ResourceList list = descriptor.getResourceList();
            list.addPostAddListener(this);
            list.addPostRemoveListener(this);
        }

        public void dispose() {
            ResourceList list = display.getDescriptor().getResourceList();
            list.removePostAddListener(this);
            list.removePostRemoveListener(this);
        }

        /**
         * Notice a resource has been removed. (non-Javadoc)
         * 
         * @see com.raytheon.uf.viz.core.rsc.ResourceList.RemoveListener#notifyRemove
         *      (com.raytheon.uf.viz.core.drawables.ResourcePair) TODO: This
         *      approach will be improved in an upcoming DR
         */
        @Override
        public void notifyRemove(ResourcePair rp) throws VizException {

            /**
             * The EnsembleResourceManager already listens for a dispose event
             * on all the resources it owns.
             */
        }

        /**
         * Notice a resource has been added. (non-Javadoc)
         * 
         * @see com.raytheon.uf.viz.core.rsc.ResourceList.AddListener#notifyAdd(com
         *      .raytheon .uf.viz.core.drawables.ResourcePair)
         */
        @Override
        public void notifyAdd(ResourcePair rp) throws VizException {

            if (EnsembleTool.getInstance().isToolAvailable()) {
                EnsembleToolLayer toolLayer = EnsembleTool.getInstance()
                        .getActiveToolLayer();
                if (toolLayer != null && toolLayer.isEditable()) {

                    if ((rp.getResource().hasCapability(
                            EditableCapability.class) == true)
                            && (!(rp.getResource() instanceof EnsembleToolLayer))) {
                        EnsembleTool.getInstance()
                                .setForeignEditableToolLoading();
                    }

                    AbstractVizResource<?, ?> rsc = rp.getResource();
                    if ((rsc != null) && (isCompatibleResource(rp))) {
                        rsc.registerListener(this);
                        if (rsc.getStatus() == ResourceStatus.INITIALIZED) {
                            EnsembleResourceManager.getInstance()
                                    .addResourceForRegistration(rsc);
                        }
                    }
                }
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * com.raytheon.uf.viz.core.rsc.IInitListener#inited(com.raytheon.uf
         * .viz.core.rsc.AbstractVizResource)
         */
        @Override
        public synchronized void inited(AbstractVizResource<?, ?> rsc) {
            if (rsc != null) {
                EnsembleResourceManager.getInstance()
                        .addResourceForRegistration(rsc);
            }
        }

        private boolean isCompatibleResource(ResourcePair rp) {

            /* ignore the tool layer itself */
            if (rp.getResource() instanceof EnsembleToolLayer) {
                return false;
            }

            /* ignore the null resources */
            AbstractVizResource<?, ?> resource = rp.getResource();
            if (resource == null) {
                return false;
            }

            /* ignore the image resources */
            if (resource instanceof D2DGridResource) {
                D2DGridResource gr = (D2DGridResource) resource;
                if (gr.getDisplayType() == DisplayType.IMAGE) {
                    return false;
                }
            }

            /*
             * If this is not a generated resource, and is a gridded or time
             * series data then the resource is compatible.
             */
            if (!isGeneratedResource(rp)
                    && ((resource instanceof GridResource) || (resource instanceof TimeSeriesResource))) {
                return true;
            }
            return false;
        }

        /**
         * Check if the resource is interested by the ensemble tool.
         * 
         * @param rp
         * @return
         */
        private boolean isGeneratedResource(ResourcePair rp) {
            if (rp.getResource() instanceof HistogramResource
                    || rp.getResource() instanceof GeneratedEnsembleGridResource
                    || rp.getResource() instanceof GeneratedTimeSeriesResource) {
                return true;
            }

            return false;
        }

        /**
         * Get the IRenderableDisplay
         * 
         * @return the display
         */
        public IRenderableDisplay getDisplay() {
            return display;
        }
    }

}