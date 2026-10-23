package com.pocket.xiaoli.data.map.model

object CampusMapTestFixtures {
    fun validData(): CampusMapData = CampusMapData(
        campusId = "ytlg_main",
        buildings = listOf(
            CampusBuilding(
                id = "b1",
                name = "教学楼A",
                entranceIds = listOf("e1"),
                floorIds = listOf("f1"),
            )
        ),
        entrances = listOf(
            CampusEntrance(id = "e1", buildingId = "b1", name = "东门")
        ),
        floors = listOf(
            CampusFloor(id = "f1", buildingId = "b1", level = 1, name = "1F", roomIds = listOf("r1"))
        ),
        rooms = listOf(
            CampusRoom(
                id = "r1",
                buildingId = "b1",
                floorId = "f1",
                name = "101",
                type = "classroom",
                nodeId = "n1",
            )
        ),
        nodes = listOf(
            CampusNode(id = "n1", buildingId = "b1", floorId = "f1", kind = "room"),
            CampusNode(id = "n2", buildingId = "b1", floorId = "f1", kind = "corridor"),
        ),
        edges = listOf(
            CampusEdge(from = "n1", to = "n2", meters = 3.0, kind = "corridor")
        ),
    )
}