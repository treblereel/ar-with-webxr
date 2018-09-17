package org.treblereel.arwithwebxr.client;

import com.google.gwt.core.client.EntryPoint;
import elemental2.dom.HTMLCanvasElement;
import elemental2.xr.XRDevice;
import elemental2.xr.XRDevicePose;
import elemental2.xr.XRFrame;
import elemental2.xr.XRFrameOfReference;
import elemental2.xr.XRNavigator;
import elemental2.xr.XRPresentationContext;
import elemental2.xr.XRSession;
import elemental2.xr.XRSessionCreationOptions;
import elemental2.xr.XRView;
import elemental2.xr.XRViewport;
import elemental2.xr.XRWebGLLayer;
import elemental2.xr.XRWebGLRenderingContext;
import jsinterop.base.Js;
import org.treblereel.gwt.three4g.cameras.PerspectiveCamera;
import org.treblereel.gwt.three4g.geometries.BoxBufferGeometry;
import org.treblereel.gwt.three4g.materials.MeshBasicMaterial;
import org.treblereel.gwt.three4g.math.Matrix4;
import org.treblereel.gwt.three4g.objects.Mesh;
import org.treblereel.gwt.three4g.renderers.WebGLRenderer;
import org.treblereel.gwt.three4g.renderers.parameters.WebGLRendererParameters;
import org.treblereel.gwt.three4g.scenes.Scene;

import static elemental2.dom.DomGlobal.document;
import static elemental2.dom.DomGlobal.navigator;

public class App implements EntryPoint {
    private WebGLRenderer renderer;
    private XRWebGLRenderingContext gl;
    private PerspectiveCamera camera;
    private XRFrameOfReference frameOfRef;
    private Scene scene;
    private XRNavigator xrNavigator;
    private XRDevice xrDevice;
    private XRSession session;

    public void onModuleLoad() {
        init();
    }

    public void init() {
        try {
            xrNavigator = XRNavigator.of(navigator);
            xrNavigator.xr.requestDevice().then(device -> {
                this.xrDevice = device;
                document.querySelector("#enter-ar").addEventListener("click", evt -> onEnterAR());
                return null;
            }, p0 -> {
                onNoXRDevice();
                return null;
            }).catch_(error -> {
                onNoXRDevice();
                return null;
            });
        } catch (ClassCastException e) {
            onNoXRDevice();
        }
    }

    public void onNoXRDevice() {
        document.body.classList.add("unsupported");
    }

    public void onEnterAR() {
        try {
            HTMLCanvasElement outputCanvas = (HTMLCanvasElement) document.createElement("canvas");
            XRPresentationContext ctx = Js.uncheckedCast(outputCanvas.getContext("xrpresent"));

            XRSessionCreationOptions options = new XRSessionCreationOptions();
            options.outputContext = Js.uncheckedCast(ctx);
            options.environmentIntegration = true;

            xrDevice.requestSession(options).then(p0 -> {
                document.body.appendChild(outputCanvas);
                onSessionStarted(p0);
                return null;
            }, error -> {
                onNoXRDevice();
                return null;
            }).catch_(error -> {
                onNoXRDevice();
                return null;
            });
        } catch (Exception e) {
            onNoXRDevice();
        }

    }

    private void onSessionStarted(XRSession session) {
        document.body.classList.add("ar");
        // Store the session for use later.
        this.session = session;

        WebGLRendererParameters parameters = new WebGLRendererParameters();
        parameters.alpha = true;
        parameters.preserveDrawingBuffer = true;

        this.renderer = new WebGLRenderer(parameters);

        this.renderer.autoClear = false;

        this.gl = XRWebGLRenderingContext.of(this.renderer.getContext());

        // Ensure that the context we want to write to is compatible
        // with our XRDevice
        this.gl.setCompatibleXRDevice(this.session.device).then(p -> {
            // Set our session's baseLayer to an XRWebGLLayer
            // using our new renderer's context
            this.session.baseLayer = new XRWebGLLayer(this.session, this.gl);

            // A THREE.Scene contains the scene graph for all objects in the
            // render scene.
            // Call our utility which gives us a THREE.Scene populated with
            // cubes everywhere.
            this.scene = createCubeScene();

            // We'll update the camera matrices directly from API, so
            // disable matrix auto updates so three.js doesn't attempt
            // to handle the matrices independently.
            this.camera = new PerspectiveCamera();
            this.camera.matrixAutoUpdate = false;

            this.session.requestFrameOfReference("eye-level").then(f -> {
                this.frameOfRef = f;
                this.session.requestAnimationFrame((timestamp, xrFrame) -> onXRFrame(timestamp, xrFrame));
                return null;
            });
            return null;
        });
    }

    private void onXRFrame(double time, XRFrame frame) {
        XRSession session = frame.session;
        XRDevicePose pose = frame.getDevicePose(this.frameOfRef);

        // Queue up the next frame
        session.requestAnimationFrame((timestamp, xrFrame) -> onXRFrame(timestamp, xrFrame));

        // Bind the framebuffer to our baseLayer's framebuffer
        this.gl.bindFramebuffer((int) this.gl.FRAMEBUFFER, ((XRWebGLLayer) session.baseLayer).framebuffer);

        if (pose != null) {
            // Our XRFrame has an array of views. In the VR case, we'll have
            // two views, one for each eye. In mobile AR, however, we only
            // have one view.
            for (XRView view : frame.views) {
                XRViewport viewport = ((XRWebGLLayer) session.baseLayer).getViewport(view);
                this.renderer.setSize(viewport.width, viewport.height);
                // Set the view matrix and projection matrix from XRDevicePose
                // and XRView onto our THREE.Camera.
                this.camera.projectionMatrix.fromArray(view.projectionMatrix);
                Matrix4 viewMatrix = new Matrix4().fromArray(pose.getViewMatrix(view));
                this.camera.matrix.getInverse(viewMatrix);
                this.camera.updateMatrixWorld(true);

                this.renderer.clearDepth();
                // Render our scene with our WebGLRenderer
                this.renderer.render(this.scene, this.camera);
            }
        }
    }


    public Scene createCubeScene() {
        Scene scene = new Scene();

        MeshBasicMaterial[] materials = {
                new MeshBasicMaterial().setColor(0xff0000),
                new MeshBasicMaterial().setColor(0x0000ff),
                new MeshBasicMaterial().setColor(0x00ff00),
                new MeshBasicMaterial().setColor(0xff00ff),
                new MeshBasicMaterial().setColor(0x00ffff),
                new MeshBasicMaterial().setColor(0xffff00)
        };

        int ROW_COUNT = 4;
        int SPREAD = 1;
        int HALF = ROW_COUNT / 2;
        for (int i = 0; i < ROW_COUNT; i++) {
            for (int j = 0; j < ROW_COUNT; j++) {
                for (int k = 0; k < ROW_COUNT; k++) {
                    Mesh box = new Mesh(new BoxBufferGeometry(0.2f, 0.2f, 0.2f), materials);
                    box.position.set(i - HALF, j - HALF, k - HALF);
                    box.position.multiplyScalar(SPREAD);
                    scene.add(box);
                }
            }
        }

        return scene;
    }

}
