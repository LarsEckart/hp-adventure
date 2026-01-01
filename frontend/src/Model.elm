module Model exposing
    ( Adventure
    , AssistantTurn
    , CompletedAdventure
    , GameState
    , Item
    , Player
    , Stats
    , Turn
    , defaultState
    , isProfileComplete
    )

type alias Item =
    { name : String
    , description : String
    , foundAt : String
    }


type alias CompletedAdventure =
    { title : String
    , summary : String
    , completedAt : String
    }


type alias Stats =
    { adventuresCompleted : Int
    , totalTurns : Int
    }

type alias Player =
    { name : String
    , houseName : String
    , inventory : List Item
    , completedAdventures : List CompletedAdventure
    , stats : Stats
    }


type alias AssistantTurn =
    { storyText : String
    , suggestedActions : List String
    , newItems : List Item
    , adventureCompleted : Bool
    }


type alias Turn =
    { userAction : String
    , assistant : Maybe AssistantTurn
    }


type alias Adventure =
    { title : Maybe String
    , startedAt : String
    , turns : List Turn
    }


type alias GameState =
    { schemaVersion : Int
    , player : Player
    , currentAdventure : Maybe Adventure
    , actionInput : String
    , isLoading : Bool
    , error : Maybe String
    , notice : Maybe String
    , showInventory : Bool
    , showHistory : Bool
    , pendingAbandon : Bool
    }


defaultState : GameState
defaultState =
    { schemaVersion = 2
    , player =
        { name = ""
        , houseName = ""
        , inventory = []
        , completedAdventures = []
        , stats = { adventuresCompleted = 0, totalTurns = 0 }
        }
    , currentAdventure = Nothing
    , actionInput = ""
    , isLoading = False
    , error = Nothing
    , notice = Nothing
    , showInventory = True
    , showHistory = True
    , pendingAbandon = False
    }


isProfileComplete : Player -> Bool
isProfileComplete player =
    String.trim player.name /= "" && String.trim player.houseName /= ""
