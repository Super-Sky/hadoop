/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.router.webapp;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.Gson;
import org.apache.hadoop.util.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterId;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.ClusterMetricsInfo;
import org.apache.hadoop.yarn.server.router.Router;
import org.apache.hadoop.yarn.webapp.hamlet2.Hamlet;
import org.apache.hadoop.yarn.webapp.hamlet2.Hamlet.TABLE;
import org.apache.hadoop.yarn.webapp.hamlet2.Hamlet.TBODY;
import org.apache.hadoop.yarn.webapp.util.WebAppUtils;

import com.google.inject.Inject;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.api.json.JSONJAXBContext;
import com.sun.jersey.api.json.JSONUnmarshaller;

class FederationBlock extends RouterBlock {

  private final Router router;

  @Inject
  FederationBlock(ViewContext ctx, Router router) {
    super(router, ctx);
    this.router = router;
  }

  @Override
  public void render(Block html) {

    boolean isEnabled = isYarnFederationEnabled();

    // init Html Page Federation
    initHtmlPageFederation(html, isEnabled);
  }

  /**
   * Parse the capability and obtain the metric information of the cluster.
   *
   * @param capability metric json obtained from RM.
   * @return ClusterMetricsInfo Object
   */
  private ClusterMetricsInfo getClusterMetricsInfo(String capability) {
    try {
      if (capability != null && !capability.isEmpty()) {
        JSONJAXBContext jc = new JSONJAXBContext(
            JSONConfiguration.mapped().rootUnwrapping(false).build(), ClusterMetricsInfo.class);
        JSONUnmarshaller unmarShaller = jc.createJSONUnmarshaller();
        StringReader stringReader = new StringReader(capability);
        ClusterMetricsInfo clusterMetrics =
            unmarShaller.unmarshalFromJSON(stringReader, ClusterMetricsInfo.class);
        return clusterMetrics;
      }
    } catch (Exception e) {
      LOG.error("Cannot parse SubCluster info", e);
    }
    return null;
  }

  /**
   * Initialize the subCluster details JavaScript of the Federation page.
   *
   * This part of the js script will control to display or hide the detailed information
   * of the subCluster when the user clicks on the subClusterId.
   *
   * We will obtain the specific information of a SubCluster,
   * including the information of Applications, Resources, and Nodes.
   *
   * @param html html object
   * @param subClusterDetailMap subCluster Detail Map
   */
  private void initFederationSubClusterDetailTableJs(Block html,
      List<Map<String, String>> subClusterDetailMap) {
    Gson gson = new Gson();
    html.script().$type("text/javascript").
        __(" var scTableData = " + gson.toJson(subClusterDetailMap) + "; ")
        .__();
    html.script(root_url("static/federation/federation.js"));
  }

  /**
   * Initialize the Html page.
   *
   * @param html html object
   */
  private void initHtmlPageFederation(Block html, boolean isEnabled) {
    List<Map<String, String>> lists = new ArrayList<>();

    // If Yarn Federation is not enabled, the user needs to be prompted.
    initUserHelpInformationDiv(html, isEnabled);

    // Table header
    TBODY<TABLE<Hamlet>> tbody =
        html.table("#rms").$class("cell-border").$style("width:100%").thead().tr()
        .th(".id", "SubCluster")
        .th(".state", "State")
        .th(".lastStartTime", "LastStartTime")
        .th(".lastHeartBeat", "LastHeartBeat")
        .th(".resources", "Resources")
        .th(".nodes", "Nodes")
        .__().__().tbody();

    try {

      // Sort the SubClusters
      List<SubClusterInfo> subclusters = getSubClusterInfoList();

      for (SubClusterInfo subcluster : subclusters) {

        Map<String, String> subclusterMap = new HashMap<>();

        // Prepare subCluster
        SubClusterId subClusterId = subcluster.getSubClusterId();
        String subClusterIdText = subClusterId.getId();

        // Prepare WebAppAddress
        String webAppAddress = subcluster.getRMWebServiceAddress();
        String herfWebAppAddress = "";
        if (webAppAddress != null && !webAppAddress.isEmpty()) {
          herfWebAppAddress =
              WebAppUtils.getHttpSchemePrefix(this.router.getConfig()) + webAppAddress;
        }

        // Prepare Capability
        String capability = subcluster.getCapability();
        ClusterMetricsInfo subClusterInfo = getClusterMetricsInfo(capability);

        // Prepare LastStartTime & LastHeartBeat
        String lastStartTime =
            DateFormatUtils.format(subcluster.getLastStartTime(), DATE_PATTERN);
        String lastHeartBeat =
            DateFormatUtils.format(subcluster.getLastHeartBeat(), DATE_PATTERN);

        // Prepare Resource
        long totalMB = subClusterInfo.getTotalMB();
        String totalMBDesc = StringUtils.byteDesc(totalMB * BYTES_IN_MB);
        long totalVirtualCores = subClusterInfo.getTotalVirtualCores();
        String resources = String.format("<Memory:%s, VCore:%s>", totalMBDesc, totalVirtualCores);

        // Prepare Node
        long totalNodes = subClusterInfo.getTotalNodes();
        long activeNodes = subClusterInfo.getActiveNodes();
        String nodes = String.format("<Total Nodes:%s, Active Nodes:%s>", totalNodes, activeNodes);

        // Prepare HTML Table
        tbody.tr().$id(subClusterIdText)
            .td().$class("details-control").a(herfWebAppAddress, subClusterIdText).__()
            .td(subcluster.getState().name())
            .td(lastStartTime)
            .td(lastHeartBeat)
            .td(resources)
            .td(nodes)
            .__();

        subclusterMap.put("subcluster", subClusterId.getId());
        subclusterMap.put("capability", capability);
        lists.add(subclusterMap);
      }
    } catch (Exception e) {
      LOG.error("Cannot render Router Federation.", e);
    }

    // Init FederationBlockTableJs
    initFederationSubClusterDetailTableJs(html, lists);

    // Tips
    tbody.__().__().div().p().$style("color:red")
        .__("*The application counts are local per subcluster").__().__();
  }
}
