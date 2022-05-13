/* Copyright 2022 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.esri.arcgisruntime.sample.evaluatearcadeexpression

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.arcade.ArcadeEvaluator
import com.esri.arcgisruntime.arcade.ArcadeExpression
import com.esri.arcgisruntime.arcade.ArcadeProfile
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.data.FeatureTable
import com.esri.arcgisruntime.data.ServiceFeatureTable
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.IdentifyLayerResult
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.portal.Portal
import com.esri.arcgisruntime.portal.PortalItem
import com.esri.arcgisruntime.sample.evaluatearcadeexpression.databinding.ActivityMainBinding
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

    private val activityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val mapView: MapView by lazy {
        activityMainBinding.mapView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activityMainBinding.root)

        // authentication with an API key or named user is required to access basemaps and other
        // location services
        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.API_KEY)

        val portal = Portal("https://www.arcgis.com")
        val portalItem = PortalItem(portal, "14562fced3474190b52d315bc19127f6")
        // create a map with the BasemapType topographic
        val map = ArcGISMap(portalItem)

        // set the map to be displayed in the layout's MapView
        mapView.map = map

        map.addDoneLoadingListener {

            val beatsLayer = map.operationalLayers.first { (it as? FeatureLayer)?.name == "RPD Beats  - City_Beats_Border_1128-4500" }

            mapView.onTouchListener =
                object : DefaultMapViewOnTouchListener(this@MainActivity, mapView) {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        val screenPoint = android.graphics.Point(
                            e.x.roundToInt(),
                            e.y.roundToInt()
                        )
                        val identifyLayerResultsFuture = mapView
                            .identifyLayerAsync(beatsLayer, screenPoint, 12.0, false, 10)

                        identifyLayerResultsFuture.addDoneListener {


                            val identifyLayerResults = identifyLayerResultsFuture.get()

                            showEvaluatedArcadeInCallout(
                                mapView.screenToLocation(screenPoint),
                                identifyLayerResults
                            )
                        }
                        return true
                    }
                }

            makeArcadeEvaluator()
        }
    }

    private fun makeArcadeEvaluator(): ArcadeEvaluator {

        val expression = """
            
            // Get a feature set of crimes from the map by referencing a layer name
            var crimes = FeatureSetByName(${'$'}map, "Crime in the last 60 days")
            
            // Count the number of crimes that intersect the provided police beat polygon
            return Count(Intersects(${'$'}feature, crimes))
            
            """

        val arcadeExpression = ArcadeExpression(expression, "Count Crimes", "Count Crimes")

        return ArcadeEvaluator(arcadeExpression, ArcadeProfile.FORM_CALCULATION)
    }

    private fun showEvaluatedArcadeInCallout(mapPoint: Point, identifyResult: IdentifyLayerResult) {

        val beatFeature = identifyResult.elements.first() as? Feature
        val beat = beatFeature?.attributes?.get("Beat") as? String
        val section = beatFeature?.attributes?.get("Section") as? String

        // set up a dictionary of variables to be used by the expression.
        // in this case the map and the identified feature
        val expressionVariables = mapOf("\$feature" to beatFeature, "\$map" to mapView.map)

        val arcadeEvaluatorAsync = makeArcadeEvaluator().evaluateAsync(expressionVariables)
        arcadeEvaluatorAsync.addDoneListener {
            val result = arcadeEvaluatorAsync.get().result

            val calloutContent = TextView(applicationContext).apply {
                setTextColor(Color.BLACK)
                text = "Beat: $beat - $section" + "\n" +
                        "$result crimes in the past two months"
            }

            mapView.callout.apply {
                content = calloutContent
                location = mapPoint
                show()
            }
        }

    }

    override fun onPause() {
        mapView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onDestroy() {
        mapView.dispose()
        super.onDestroy()
    }
}
