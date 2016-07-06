package com.lynbrookrobotics.funkydashboard

import japgolly.scalajs.react.vdom.all._
import japgolly.scalajs.react.{BackendScope, Callback, ReactComponentB}
import org.scalajs.dom.ext.Ajax

import scala.scalajs.js
import scala.scalajs.js.JSON
import com.payalabs.scalajs.react.mdl._
import org.scalajs.dom._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object DashboardContainer {
  class Backend($: BackendScope[Unit, List[DatasetGroup]]) {
    def componentDidMount: Callback = Callback {
      Ajax.get("/datasets.json").foreach { result =>
        $.setState(JSON.parse(result.responseText).asInstanceOf[js.Array[DatasetGroup]].toList).runNow()
      }
    }

    def render(state: List[DatasetGroup]) = {
      div(
        Dashboard(state)
      )
    }
  }

  val component = ReactComponentB[Unit](getClass.getSimpleName)
    .initialState(List.empty[DatasetGroup])
    .renderBackend[Backend]
    .componentDidMount(_.backend.componentDidMount)
    .build

  def apply() = component()
}

object Dashboard {
  case class Props(groups: List[DatasetGroup])

  class Backend($: BackendScope[Props, (Boolean, Int, List[Map[String, Map[String, js.Any]]])]) {
    val websocketProtocol = if (window.location.protocol == "https") {
      "wss"
    } else {
      "ws"
    }

    def componentDidMount: Callback = Callback {
      val datastream = new WebSocket(s"$websocketProtocol://${window.location.host}/datastream")
      datastream.onmessage = (e: MessageEvent) => {
        val newValues = List(JSON.parse(e.data.toString).asInstanceOf[js.Dictionary[js.Any]].
          toMap.mapValues(_.asInstanceOf[js.Dictionary[js.Any]].toMap))
        $.modState { state =>
          state.copy(
            _3 = (state._3 ++ newValues).takeRight(50)
          )
        }.runNow()
      }
    }

    def render(props: Props, state: (Boolean, Int, List[Map[String, Map[String, js.Any]]])) = {
      import props._
      val (paused, activeGroupIndex, pointsWindow) = state

      div(className := "mdl-layout mdl-js-layout mdl-layout--fixed-drawer mdl-layout--fixed-header")(
        header(className := "mdl-layout__header")(
          div(className := "mdl-layout__header-row")(
            span(className := "mdl-layout-title")("Funky Dashboard"),
            div(className := "mdl-layout-spacer"),
            button(
              id := "pause-button",
              className := "mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent"
            )("Toggle Pause").material
          )
        ),
        div(className := "mdl-layout__drawer mdl-color--blue-grey-900 mdl-color-text--blue-grey-50")(
          header(
            img(src := "/images/Team_846_Logo.png", className := "team-logo")
          ),
          nav(id := "group-chooser", className := "group-chooser mdl-navigation mdl-color--blue-grey-800")(
            groups.zipWithIndex.map { case (group, index) =>
              a(
                className := "mdl-navigation__link",
                backgroundColor := (if (index == activeGroupIndex) "#00ACC1" else "transparent"),
                cursor := "pointer",
                onClick --> $.modState(_.copy(_2 = index))
              )(group.name)
            }
          )
        ),
        main(className := "mdl-layout__content mdl-color--grey-100", id := "groups-container")(
          div(className := "mdl-grid")(
            if (activeGroupIndex < groups.size) {
              val group = groups(activeGroupIndex)
              group.datasets.map { d =>
                DatasetCard(
                  d.name,
                  Dataset.extract(d)(pointsWindow.flatMap(_.get(group.name).flatMap(_.get(d.name))))
                )
              }
            } else {
              h1("Loading")
            }
          )
        )
      ).material
    }
  }

  val component = ReactComponentB[Props](getClass.getSimpleName)
    .initialState((false, 0, List.empty[Map[String, Map[String, js.Any]]]))
    .renderBackend[Backend]
    .componentDidMount(_.backend.componentDidMount)
    .build

  def apply(groups: List[DatasetGroup]) =
    component(Props(groups))
}
