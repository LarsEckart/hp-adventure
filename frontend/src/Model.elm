module Model exposing
    ( Adventure
    , AssistantTurn
    , GameState
    , Player
    , Turn
    , defaultState
    , isProfileComplete
    )

type alias Player =
    { name : String
    , houseName : String
    }


type alias AssistantTurn =
    { storyText : String
    , suggestedActions : List String
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
    }


defaultState : GameState
defaultState =
    { schemaVersion = 1
    , player = { name = "", houseName = "" }
    , currentAdventure = Nothing
    , actionInput = ""
    , isLoading = False
    , error = Nothing
    }


isProfileComplete : Player -> Bool
isProfileComplete player =
    String.trim player.name /= "" && String.trim player.houseName /= ""
